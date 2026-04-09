# Problema N+1 — Diagnóstico e Solução Completa

O N+1 é o problema de performance mais comum em aplicações JPA/Hibernate.
Acontece quando você faz 1 query para buscar uma lista de entidades, e depois
o Hibernate dispara N queries extras para carregar os relacionamentos de cada uma.

---

## Como identificar

### Pelo log
Com `show-sql: true`, você verá algo assim:

```sql
-- 1 query principal
SELECT * FROM pedido WHERE status = 'ATIVO'

-- N queries extras (uma por pedido!)
SELECT * FROM item WHERE pedido_id = 1
SELECT * FROM item WHERE pedido_id = 2
SELECT * FROM item WHERE pedido_id = 3
-- ... e assim por diante
```

### Pela estatística do Hibernate
```yaml
spring.jpa.properties.hibernate.generate_statistics: true
logging.level.org.hibernate.stat: DEBUG
```

Procure nos logs: `StatisticalLoggingSessionEventListener` — mostrará o total de queries por requisição.

---

## Soluções

### 1. JOIN FETCH (mais comum)

```java
// ❌ Causa N+1
@OneToMany(fetch = FetchType.EAGER)  // EAGER é o vilão principal
private List<Item> itens;

// ✅ Solução: LAZY + JOIN FETCH explícito quando necessário
@OneToMany(fetch = FetchType.LAZY)
private List<Item> itens;

// No repository, busca com JOIN quando precisar dos itens
@Query("SELECT p FROM Pedido p JOIN FETCH p.itens WHERE p.status = :status")
List<Pedido> findAtivosComItens(@Param("status") String status);

// Quando não precisar dos itens, usa a query simples
List<Pedido> findByStatus(String status);
```

### 2. @EntityGraph (alternativa ao JOIN FETCH)

```java
@EntityGraph(attributePaths = {"itens", "cliente"})
@Query("SELECT p FROM Pedido p WHERE p.status = :status")
List<Pedido> findAtivosComItensECliente(@Param("status") String status);
```

### 3. Batch Size (solução paliativa)

Quando não é possível usar JOIN FETCH, o Hibernate pode buscar em lotes:

```java
@OneToMany(fetch = FetchType.LAZY)
@BatchSize(size = 30)  // ao invés de N queries, faz N/30 queries
private List<Item> itens;
```

Ou globalmente no `application.yml`:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 30
```

### 4. Projeção com DTO (elimina o problema por completo)

Quando você só precisa de alguns campos, use projeção — sem relacionamentos, sem N+1:

```java
// Interface de projeção
public interface PedidoResumo {
    Long getId();
    String getStatus();
    Integer getTotalItens();  // calculado na query
}

// Query com aggregate — zero N+1
@Query("""
    SELECT p.id as id, p.status as status, COUNT(i) as totalItens
    FROM Pedido p LEFT JOIN p.itens i
    GROUP BY p.id, p.status
""")
List<PedidoResumo> findResumos();
```

---

## Armadilhas comuns

### EAGER em relacionamentos
```java
// ❌ Nunca use EAGER — causa N+1 automaticamente em qualquer listagem
@ManyToOne(fetch = FetchType.EAGER)
private Cliente cliente;

// ✅ Sempre LAZY
@ManyToOne(fetch = FetchType.LAZY)
private Cliente cliente;
```

### Chamar métodos lazy fora de transação
```java
// ❌ Erro clássico — LazyInitializationException
Pedido pedido = pedidoRepo.findById(id).get();
// transação fechou aqui no service sem @Transactional
pedido.getItens().size();  // ERRO: sessão já fechada

// ✅ Garantir transação aberta
@Transactional(readOnly = true)
public PedidoDTO findById(Long id) {
    Pedido pedido = pedidoRepo.findById(id).orElseThrow();
    pedido.getItens().size();  // OK: dentro da transação
    return mapper.toDTO(pedido);
}
```

### Serialização JSON disparando lazy load
```java
// ❌ Jackson tentando serializar coleção lazy = N+1 invisível
@RestController
public class PedidoController {
    public List<Pedido> listar() {
        return service.findAll();  // Jackson serializa itens = N+1!
    }
}

// ✅ Sempre retorne DTOs, nunca entidades diretamente
public List<PedidoDTO> listar() {
    return service.findAllDTO();  // DTO não tem relacionamentos lazy
}
```
