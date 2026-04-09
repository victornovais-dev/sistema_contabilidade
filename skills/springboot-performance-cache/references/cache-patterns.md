# Padrões Avançados de Cache — Spring Boot + Redis

---

## TTL diferente por tipo de entidade

```java
@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer cacheManagerCustomizer() {
        return builder -> builder
            // Produtos mudam pouco — cache por 1 hora
            .withCacheConfiguration("produtos",
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofHours(1)))

            // Dashboard muda frequentemente — cache por 1 minuto
            .withCacheConfiguration("dashboard",
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofMinutes(1)))

            // Configurações do sistema — cache por 1 dia
            .withCacheConfiguration("config",
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofDays(1)));
    }
}
```

---

## Cache condicional

```java
@Service
public class RelatorioService {

    // Só cacheia se o resultado não for nulo
    @Cacheable(value = "relatorios", key = "#mes", unless = "#result == null")
    public Relatorio getRelatorio(String mes) {
        return repository.findByMes(mes);
    }

    // Só cacheia para usuários admin
    @Cacheable(value = "dados-sensiveis", condition = "#usuario.isAdmin()")
    public List<DadoSensivel> getDados(Usuario usuario) {
        return repository.findAll();
    }
}
```

---

## Cache com chave composta

```java
@Cacheable(value = "pedidos", key = "#clienteId + '-' + #status + '-' + #pagina")
public Page<Pedido> findByClienteEStatus(Long clienteId, String status, int pagina) {
    return repository.findByClienteIdAndStatus(
        clienteId, status, PageRequest.of(pagina, 20)
    );
}
```

---

## Cache warm-up (pré-aquecimento)

Popula o cache ao iniciar a aplicação, evitando lentidão nas primeiras chamadas:

```java
@Component
public class CacheWarmUp implements ApplicationRunner {

    @Autowired private ProdutoService produtoService;
    @Autowired private ConfigService configService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Aquecendo caches...");
        produtoService.findAll();        // popula cache "produtos-lista"
        configService.getConfiguracoes(); // popula cache "config"
        log.info("Caches aquecidos.");
    }
}
```

---

## Evict em cascata (múltiplas chaves)

```java
@Service
public class CategoriaService {

    // Ao deletar categoria, invalida ela E todos os produtos da categoria
    @Caching(evict = {
        @CacheEvict(value = "categorias",      key = "#id"),
        @CacheEvict(value = "categorias-lista", allEntries = true),
        @CacheEvict(value = "produtos-lista",   allEntries = true),
        @CacheEvict(value = "dashboard",        allEntries = true)
    })
    public void deletar(Long id) {
        repository.deleteById(id);
    }
}
```

---

## Cache programático (sem anotação)

Para casos complexos onde a anotação não é suficiente:

```java
@Service
public class ProdutoService {

    @Autowired
    private CacheManager cacheManager;

    public void invalidarProduto(Long id) {
        Cache cache = cacheManager.getCache("produtos");
        if (cache != null) {
            cache.evict(id);
        }
    }

    public void limparTudoRedis() {
        cacheManager.getCacheNames()
            .forEach(name -> cacheManager.getCache(name).clear());
    }
}
```

---

## Monitoramento de cache com Actuator

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```yaml
management:
  endpoints:
    web:
      exposure:
        include: caches, health, metrics
```

Endpoints disponíveis:
- `GET /actuator/caches` — lista todos os caches e entradas
- `DELETE /actuator/caches/{nome}` — limpa um cache específico
- `GET /actuator/metrics/cache.gets` — hits e misses por cache

---

## Cache distribuído (múltiplas instâncias)

Quando a aplicação roda em múltiplos pods/instâncias, use Redis como cache centralizado
para que todos compartilhem o mesmo estado:

```yaml
spring:
  cache:
    type: redis  # redis é compartilhado entre instâncias
  redis:
    host: ${REDIS_HOST:localhost}
    port: 6379
    password: ${REDIS_PASSWORD:}
    ssl: ${REDIS_SSL:false}
    cluster:
      nodes: ${REDIS_CLUSTER_NODES:}  # para Redis Cluster
```

> ⚠️ Cache em memória (`type: simple` ou `type: caffeine`) NÃO funciona
> para múltiplas instâncias — cada pod teria seu próprio cache isolado.
