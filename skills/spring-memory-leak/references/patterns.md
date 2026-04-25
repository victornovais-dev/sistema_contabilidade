# Catálogo de Padrões de Vazamento de Memória — Spring Boot / Java

## 1. Coleções Estáticas Sem Limite

**Risco:** CRÍTICO

Coleções `static` crescem indefinidamente se nunca limpas.

```java
// ❌ PROBLEMÁTICO
public class UserService {
    private static final List<User> cache = new ArrayList<>(); // nunca limpo!
    
    public void addUser(User u) {
        cache.add(u); // acumula para sempre
    }
}

// ✅ CORRETO
// Use Caffeine, Guava Cache ou Redis com TTL/maxSize
@Bean
public Cache<String, User> userCache() {
    return Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build();
}
```

**Como detectar no código:** `static.*List|static.*Map|static.*Set` sem `clear()` ou `remove()`.

---

## 2. ThreadLocal Sem remove()

**Risco:** CRÍTICO em ambientes com thread pool (todos os app servers)

`ThreadLocal` em thread pools nunca são coletados se não fizer `remove()`.

```java
// ❌ PROBLEMÁTICO
private static final ThreadLocal<UserContext> userContext = new ThreadLocal<>();

public void process(User u) {
    userContext.set(new UserContext(u));
    doWork(); // se lançar exceção, remove() nunca é chamado
}

// ✅ CORRETO
public void process(User u) {
    userContext.set(new UserContext(u));
    try {
        doWork();
    } finally {
        userContext.remove(); // SEMPRE no finally
    }
}
```

**Como detectar:** `ThreadLocal` com `.set(` mas sem `.remove(` no mesmo método ou sem bloco `finally`.

---

## 3. Event Listeners Não Desregistrados

**Risco:** ALTO

Listeners adicionados programaticamente sem remoção criam referências que impedem GC.

```java
// ❌ PROBLEMÁTICO
@Component
public class MyService implements InitializingBean {
    @Autowired
    private ApplicationEventPublisher publisher;
    
    // Adiciona listener mas nunca remove
    public void setup() {
        someExternalLib.addListener(new MyListener()); 
    }
}

// ✅ CORRETO
@Component
public class MyService implements DisposableBean {
    private MyListener listener;
    
    public void setup() {
        listener = new MyListener();
        someExternalLib.addListener(listener);
    }
    
    @Override
    public void destroy() {
        someExternalLib.removeListener(listener); // remove no destroy
    }
}
```

**Como detectar:** `addListener(` sem correspondente `removeListener(`.

---

## 4. @Cacheable Sem TTL ou Tamanho Máximo

**Risco:** ALTO

Cache sem limite cresce até esgotar heap.

```java
// ❌ PROBLEMÁTICO
@Cacheable("products") // sem configuração de eviction!
public Product findById(Long id) { ... }

// application.yml sem configurar o cache
spring:
  cache:
    type: simple  # sem limite!

// ✅ CORRETO (Caffeine)
spring:
  cache:
    caffeine:
      spec: maximumSize=500,expireAfterWrite=600s
    cache-names: products
```

**Como detectar:** `@Cacheable` presente + `application.yml` sem `caffeine.spec` ou `redis.time-to-live`.

---

## 5. Recursos Não Fechados (Streams, Connections)

**Risco:** CRÍTICO

Streams e conexões não fechadas esgotam memória nativa e heap.

```java
// ❌ PROBLEMÁTICO
public String readFile(String path) throws IOException {
    BufferedReader reader = new BufferedReader(new FileReader(path));
    return reader.readLine(); // reader nunca fechado!
}

// ❌ PROBLEMÁTICO — Connection
public void query(DataSource ds) throws SQLException {
    Connection conn = ds.getConnection();
    Statement stmt = conn.createStatement();
    ResultSet rs = stmt.executeQuery("SELECT ...");
    // Nenhum close!
}

// ✅ CORRETO — try-with-resources
public String readFile(String path) throws IOException {
    try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
        return reader.readLine();
    }
}
```

**Como detectar:** `new BufferedReader|new FileInputStream|new FileOutputStream|new ObjectInputStream|getConnection()` sem `try (` ou sem `.close()` em `finally`.

---

## 6. Inner Classes Anônimas Segurando Referência ao Outer

**Risco:** MÉDIO-ALTO

Classes anônimas e internas não-estáticas mantêm referência implícita ao objeto externo.

```java
// ❌ PROBLEMÁTICO
public class HeavyService {
    private byte[] bigData = new byte[10_000_000]; // 10MB

    public Runnable createTask() {
        return new Runnable() { // segura referência a HeavyService!
            @Override
            public void run() {
                System.out.println("task");
            }
        };
    }
}

// ✅ CORRETO — static inner class ou lambda sem capturar this
public Runnable createTask() {
    return () -> System.out.println("task"); // não captura this se não usar campos
}
```

**Como detectar:** `new Runnable()|new Callable()|new Comparator()` (classes anônimas) dentro de classes com campos grandes.

---

## 7. Beans com Escopo Incorreto (@SessionScoped acumulando estado)

**Risco:** ALTO em aplicações com muitos usuários

Beans `@SessionScoped` com dados grandes ficam na memória até a sessão expirar.

```java
// ❌ PROBLEMÁTICO
@Component
@SessionScope
public class ShoppingCart {
    private List<Product> items = new ArrayList<>();
    private byte[] cachedCatalog = loadFullCatalog(); // 50MB por sessão!
}

// ✅ CORRETO — armazene apenas referências leves
@Component
@SessionScope  
public class ShoppingCart {
    private List<Long> itemIds = new ArrayList<>(); // apenas IDs
}
```

**Como detectar:** `@SessionScope` + campos com tipos `byte[]`, `List<...>` com objetos grandes, ou coleções não limitadas.

---

## 8. ClassLoader Leaks (Deploy em Containers)

**Risco:** CRÍTICO em hot-redeploy (Tomcat, WildFly)

Drivers JDBC e logging frameworks carregados pelo webapp ClassLoader não são descarregados.

```java
// ❌ PROBLEMÁTICO — sem deregistrar driver
@WebListener
public class AppContextListener implements ServletContextListener {
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // esqueceu de deregistrar drivers JDBC!
    }
}

// ✅ CORRETO
@Override
public void contextDestroyed(ServletContextEvent sce) {
    Enumeration<Driver> drivers = DriverManager.getDrivers();
    while (drivers.hasMoreElements()) {
        Driver driver = drivers.nextElement();
        try {
            DriverManager.deregisterDriver(driver);
        } catch (SQLException e) {
            log.error("Error deregistering driver", e);
        }
    }
}
```

**Como detectar:** `ServletContextListener` sem `DriverManager.deregisterDriver` no `contextDestroyed`.

---

## 9. String.intern() Abusado

**Risco:** MÉDIO

`String.intern()` coloca strings no pool permanente (Metaspace). Uso excessivo pode encher o Metaspace.

```java
// ❌ PROBLEMÁTICO
public void process(List<String> data) {
    for (String s : data) {
        String interned = s.intern(); // pool cresce indefinidamente
        map.put(interned, value);
    }
}
```

**Como detectar:** `.intern()` dentro de loops ou com dados dinâmicos (IDs, tokens, UUIDs).

---

## 10. Interceptors/Filters com Estado

**Risco:** MÉDIO-ALTO

Interceptors Spring MVC e Filters de Servlet são singletons — estado acumulado é global.

```java
// ❌ PROBLEMÁTICO
@Component
public class LoggingInterceptor implements HandlerInterceptor {
    private List<String> requestLog = new ArrayList<>(); // cresce para sempre!
    
    @Override
    public boolean preHandle(...) {
        requestLog.add(request.getRequestURI());
        return true;
    }
}

// ✅ CORRETO — use logger externo ou circular buffer com limite
private final Deque<String> requestLog = new ArrayDeque<>(1000);
```

**Como detectar:** `implements HandlerInterceptor|implements Filter` com campos `List<>|Map<>|Set<>` mutáveis.

---

## 11. Circular Reference com Proxies Spring

**Risco:** MÉDIO

Referências circulares podem criar proxies em loop que nunca são coletados em certos cenários.

```java
// ❌ PROBLEMÁTICO
@Service
public class ServiceA {
    @Autowired
    private ServiceB serviceB;
}

@Service  
public class ServiceB {
    @Autowired
    private ServiceA serviceA; // circular!
}
```

**Como detectar:** Cruzamento de `@Autowired` entre beans — ServiceA injeta ServiceB que injeta ServiceA.

---

## 12. ObjectOutputStream / Serialização Acumulada

**Risco:** MÉDIO

`ObjectOutputStream` mantém referências a todos os objetos escritos para evitar duplicatas — o back-reference table cresce.

```java
// ❌ PROBLEMÁTICO
ObjectOutputStream oos = new ObjectOutputStream(baos);
while (moreData) {
    oos.writeObject(nextObject()); // back-ref table cresce!
}

// ✅ CORRETO — reset periódico
ObjectOutputStream oos = new ObjectOutputStream(baos);
int count = 0;
while (moreData) {
    oos.writeObject(nextObject());
    if (++count % 100 == 0) {
        oos.reset(); // limpa a tabela de referências
    }
}
```

---

## Sinais de Alerta no Código (Regex para busca rápida)

```
static.*(?:List|Map|Set|Collection|ArrayList|HashMap|HashSet)\s*[<=]
ThreadLocal.*\.set\(   (sem .remove() próximo)
addListener\(          (sem removeListener próximo)
@Cacheable             (verificar config de TTL)
new.*Reader\(|new.*Stream\(   (sem try-with-resources)
\.intern\(\)           (dentro de loops)
new Runnable\(\)|new Callable\(\)   (inner classes anônimas)
@SessionScope          (verificar tamanho dos campos)
```
