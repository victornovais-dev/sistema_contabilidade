---
name: springboot-performance-cache
description: >
  Otimização de performance e cache em aplicações Spring Boot. Use este skill sempre que o usuário mencionar
  lentidão em APIs, páginas demorando para carregar, muitas chamadas ao banco, N+1 problem, cache desatualizado,
  itens excluídos ainda aparecendo, configuração do Redis, @Cacheable, @CacheEvict, CompletableFuture, HikariCP,
  paginação, projeções JPA, ou qualquer problema de performance em Spring Boot. Trigger obrigatório para qualquer
  combinação de: Spring Boot + lento, Spring Boot + cache, Spring Boot + banco de dados + performance,
  Spring Boot + Redis, ou quando o usuário perguntar como melhorar, otimizar ou acelerar APIs Spring Boot.
---

# Spring Boot — Performance & Cache

Guia completo para diagnosticar e resolver problemas de performance em APIs e páginas Spring Boot.

---

## 1. Diagnóstico Rápido

Antes de qualquer otimização, habilite o log de queries para entender o problema:

```yaml
# application.yml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        generate_statistics: true
logging:
  level:
    org.hibernate.stat: DEBUG
```

**Sinais de alerta nos logs:**
- Muitas queries repetidas → falta de cache
- Dezenas de queries por requisição → problema N+1
- Timeout de conexão → pool mal configurado
- Queries trazendo todas as colunas → falta de projeção

---

## 2. Cache com Spring Cache + Redis

### Setup

```xml
<!-- pom.xml -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  cache:
    type: redis
  redis:
    host: localhost
    port: 6379
    time-to-live: 600000  # 10 minutos em ms
```

```java
@EnableCaching
@SpringBootApplication
public class Application {}
```

### Anotações principais

| Anotação | Função |
|---|---|
| `@Cacheable` | Salva resultado no cache; próximas chamadas retornam do cache |
| `@CacheEvict` | Remove entrada do cache |
| `@CachePut` | Atualiza cache sem pular a execução do método |
| `@Caching` | Combina múltiplas operações de cache em um só método |

### Padrão completo (busca + lista + exclusão)

```java
@Service
public class ProdutoService {

    // Cacheia por ID
    @Cacheable(value = "produtos", key = "#id")
    public Produto findById(Long id) {
        return repository.findById(id).orElseThrow();
    }

    // Cacheia lista completa
    @Cacheable(value = "produtos-lista")
    public List<Produto> findAll() {
        return repository.findAll();
    }

    // Atualiza item E invalida a lista
    @Caching(
        put    = { @CachePut(value = "produtos", key = "#produto.id") },
        evict  = { @CacheEvict(value = "produtos-lista", allEntries = true) }
    )
    public Produto salvar(Produto produto) {
        return repository.save(produto);
    }

    // Remove item do cache E invalida a lista — item some IMEDIATAMENTE
    @Caching(evict = {
        @CacheEvict(value = "produtos",       key = "#id"),
        @CacheEvict(value = "produtos-lista", allEntries = true)
    })
    public void deletar(Long id) {
        repository.deleteById(id);
    }
}
```

> ⚠️ **Regra importante:** sempre que existir cache de lista + cache por ID,
> use `@Caching` para limpar os dois juntos na exclusão e atualização.
> Senão a lista continua mostrando itens deletados.

---

## 3. Chamadas Paralelas com CompletableFuture

Quando a página carrega múltiplas fontes de dados independentes, execute em paralelo:

```java
// Configuração do pool de threads
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(10);
        exec.setMaxPoolSize(20);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("async-");
        exec.initialize();
        return exec;
    }
}
```

```java
@Service
public class DashboardService {

    @Async
    public CompletableFuture<List<Pedido>> getPedidos() {
        return CompletableFuture.completedFuture(pedidoRepo.findAll());
    }

    @Async
    public CompletableFuture<List<Cliente>> getClientes() {
        return CompletableFuture.completedFuture(clienteRepo.findAll());
    }

    @Async
    public CompletableFuture<Resumo> getResumoFinanceiro() {
        return CompletableFuture.completedFuture(financeiroRepo.getResumo());
    }

    public DashboardDTO getDashboard() {
        // Dispara as 3 ao mesmo tempo
        var pedidos   = getPedidos();
        var clientes  = getClientes();
        var financeiro = getResumoFinanceiro();

        // Espera todas terminarem
        CompletableFuture.allOf(pedidos, clientes, financeiro).join();

        return new DashboardDTO(
            pedidos.join(),
            clientes.join(),
            financeiro.join()
        );
    }
}
```

**Ganho:** se cada chamada leva 500ms, sem paralelo = 1500ms, com paralelo = ~500ms.

---

## 4. Problema N+1 (o vilão silencioso)

O problema mais comum e mais danoso. Acontece quando o Hibernate faz 1 query principal
e depois N queries extras para buscar relacionamentos.

```java
// ❌ RUIM — gera 1 + N queries
List<Pedido> pedidos = pedidoRepo.findAll();           // 1 query
pedidos.forEach(p -> p.getItens().size());             // N queries extras!

// ✅ BOM — tudo em 1 query com JOIN FETCH
@Query("SELECT p FROM Pedido p JOIN FETCH p.itens WHERE p.status = :status")
List<Pedido> findComItens(@Param("status") String status);

// ✅ BOM — configurar LAZY no relacionamento (padrão recomendado)
@OneToMany(fetch = FetchType.LAZY)
private List<Item> itens;
```

Para mais detalhes e exemplos → veja `references/n1-problem.md`

---

## 5. Paginação e Projeções

Nunca carregue mais dados do que a tela precisa.

```java
// Projeção — interface com só os campos necessários
public interface ProdutoResumo {
    Long getId();
    String getNome();
    BigDecimal getPreco();
    // não traz foto, descrição longa, histórico, etc.
}

// Repository com paginação + projeção
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    @Query("SELECT p.id as id, p.nome as nome, p.preco as preco FROM Produto p")
    Page<ProdutoResumo> findAllResumo(Pageable pageable);
}

// Uso no Service
Page<ProdutoResumo> pagina = repository.findAllResumo(
    PageRequest.of(0, 20, Sort.by("nome"))
);
```

---

## 6. HikariCP — Pool de Conexões

Configuração recomendada para produção:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20       # máximo de conexões simultâneas
      minimum-idle: 5             # conexões mantidas abertas em repouso
      connection-timeout: 30000   # tempo máximo esperando conexão do pool (ms)
      idle-timeout: 600000        # conexão ociosa é fechada após 10 min
      max-lifetime: 1800000       # conexão vive no máximo 30 min
      pool-name: HikariPool-App
```

> Para tamanho do pool: uma boa referência é `(núcleos da CPU * 2) + disco_spindle`.
> Para bancos em nuvem (RDS, Cloud SQL), geralmente 10-20 é suficiente.

---

## 7. Checklist de Performance

Ao investigar lentidão em uma API Spring Boot, siga esta ordem:

1. **Habilitar log de queries** → identificar quantidade e padrão
2. **Verificar N+1** → adicionar JOIN FETCH ou `@EntityGraph`
3. **Adicionar cache** → `@Cacheable` nas queries mais chamadas
4. **Usar projeções** → não trazer colunas desnecessárias
5. **Paginar resultados** → nunca retornar lista sem limite
6. **Paralelizar chamadas independentes** → `@Async` + `CompletableFuture`
7. **Ajustar HikariCP** → pool adequado ao número de threads

---

## Referências detalhadas

- `references/n1-problem.md` — Diagnóstico e solução completa do problema N+1
- `references/cache-patterns.md` — Padrões avançados de cache (TTL por entidade, cache condicional, warm-up)
