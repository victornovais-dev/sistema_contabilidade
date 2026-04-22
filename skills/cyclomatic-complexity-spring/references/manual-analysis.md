# Análise Manual de Complexidade Ciclomática

## Algoritmo de Contagem

```
CC = 1
Para cada token abaixo encontrado no método, some +1:
  if, else if, for, foreach, while, do-while
  case (em switch)
  catch (em try-catch)
  && (AND lógico)
  || (OR lógico)
  ?: (operador ternário)
  assert
```

## Exemplo Passo a Passo

```java
// CC = 1 (início)
public String processPayment(Order order) {
    if (order == null) {              // +1 → CC=2
        throw new IllegalArgumentException("Order null");
    }
    
    if (order.getTotal() > 1000       // +1 → CC=3
        && order.isPremiumCustomer()) { // +1 (&&) → CC=4
        applyDiscount(order);
    } else if (order.hasCoupon()) {   // +1 → CC=5
        applyCoupon(order);
    }
    
    for (Item item : order.getItems()) { // +1 → CC=6
        if (item.isOutOfStock()) {       // +1 → CC=7
            return "UNAVAILABLE";
        }
    }
    
    try {
        chargeCard(order);
    } catch (PaymentException e) {    // +1 → CC=8
        return "PAYMENT_FAILED";
    } catch (NetworkException e) {    // +1 → CC=9
        return "RETRY";
    }
    
    return order.getStatus() == PAID  // +1 ternário → CC=10
        ? "SUCCESS" : "PENDING";
}
// RESULTADO FINAL: CC = 10 ⚠️ (limite aceitável)
```

## Casos Especiais Java/Spring

### Lambdas e Streams
Lambdas **não** incrementam CC do método pai, mas complexidade interna conta:
```java
// A lambda dentro não conta para CC do método processOrders
orders.stream()
    .filter(o -> o.getTotal() > 100)  // não conta para CC do método pai
    .map(this::transform)
    .collect(toList());
```

### Switch Expressions (Java 14+)
```java
// Cada "case" = +1
String result = switch (status) {
    case PENDING -> "aguardando";    // +1
    case PAID    -> "pago";          // +1
    case FAILED  -> "falhou";        // +1
    default      -> "desconhecido";  // não conta (default)
};
// CC += 3
```

### Optional com ifPresent/orElse
```java
// ifPresent e orElseGet contam como +1 cada (são branches lógicos)
optional.ifPresent(v -> process(v));  // +1
optional.orElseGet(() -> fallback()); // +1
```

## Template de Relatório

```
📊 ANÁLISE: [NomeClasse].[nomeMetodo]()
──────────────────────────────────────
Linha de início: XX
Linhas de código: XX

Estruturas encontradas:
  if/else if:   X ocorrências  (+X)
  for/while:    X ocorrências  (+X)
  catch:        X ocorrências  (+X)
  &&/||:        X ocorrências  (+X)
  ?:            X ocorrências  (+X)
  case:         X ocorrências  (+X)

CC Base:        1
CC Total:       XX
Risco:          [BAIXO/MÉDIO/ALTO/CRÍTICO]

Recomendação:   [...]
```
