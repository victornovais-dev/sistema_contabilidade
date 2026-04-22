# Padrões de Refatoração para Alta CC em Spring Boot

## 1. Guard Clauses (mais simples)

**Antes** — CC = 8 (if aninhados):
```java
public String processOrder(Order order) {
    if (order != null) {
        if (order.isActive()) {
            if (order.getItems().size() > 0) {
                if (order.getCustomer() != null) {
                    return "PROCESSED";
                }
            }
        }
    }
    return "INVALID";
}
```

**Depois** — CC = 4:
```java
public String processOrder(Order order) {
    if (order == null) return "INVALID";
    if (!order.isActive()) return "INVALID";
    if (order.getItems().isEmpty()) return "INVALID";
    if (order.getCustomer() == null) return "INVALID";
    return "PROCESSED";
}
```

---

## 2. Extract Method

**Antes** — CC = 15 em um só método:
```java
@Service
public class OrderService {
    public void processOrder(Order order) {
        // Validação (CC +4)
        if (order == null) throw new IllegalArgumentException();
        if (!order.isValid()) throw new IllegalStateException();
        if (order.getTotal() <= 0) throw new IllegalArgumentException();
        if (order.getCustomer() == null) throw new IllegalArgumentException();
        
        // Desconto (CC +3)
        if (order.isPremium() && order.getTotal() > 500) {
            order.applyDiscount(0.10);
        } else if (order.hasCoupon()) {
            order.applyDiscount(0.05);
        }
        
        // Pagamento (CC +4)
        try {
            if (order.getPaymentMethod() == CREDIT) {
                chargeCredit(order);
            } else {
                chargePix(order);
            }
        } catch (PaymentException e) {
            handlePaymentError(order, e);
        }
        // ... mais lógica
    }
}
```

**Depois** — CC máxima = 4 por método:
```java
@Service
public class OrderService {
    
    public void processOrder(Order order) {  // CC = 1
        validateOrder(order);
        applyDiscounts(order);
        processPayment(order);
    }
    
    private void validateOrder(Order order) {  // CC = 4
        if (order == null) throw new IllegalArgumentException("Order null");
        if (!order.isValid()) throw new IllegalStateException("Order inválida");
        if (order.getTotal() <= 0) throw new IllegalArgumentException("Total inválido");
        if (order.getCustomer() == null) throw new IllegalArgumentException("Cliente null");
    }
    
    private void applyDiscounts(Order order) {  // CC = 3
        if (order.isPremium() && order.getTotal() > 500) {
            order.applyDiscount(0.10);
        } else if (order.hasCoupon()) {
            order.applyDiscount(0.05);
        }
    }
    
    private void processPayment(Order order) {  // CC = 3
        try {
            if (order.getPaymentMethod() == CREDIT) {
                chargeCredit(order);
            } else {
                chargePix(order);
            }
        } catch (PaymentException e) {
            handlePaymentError(order, e);
        }
    }
}
```

---

## 3. Strategy Pattern (para múltiplos tipos)

**Antes** — CC = 12 (muito `if/else` por tipo):
```java
public BigDecimal calculateShipping(Order order) {
    if (order.getShippingType() == EXPRESS) {
        if (order.getWeight() > 10) return BigDecimal.valueOf(50);
        else return BigDecimal.valueOf(25);
    } else if (order.getShippingType() == STANDARD) {
        if (order.getRegion().equals("SP")) return BigDecimal.valueOf(10);
        else if (order.getRegion().equals("RJ")) return BigDecimal.valueOf(15);
        else return BigDecimal.valueOf(20);
    } else if (order.getShippingType() == FREE) {
        return BigDecimal.ZERO;
    }
    // ...
}
```

**Depois** — CC = 2 no método principal:
```java
// Interface
public interface ShippingStrategy {
    BigDecimal calculate(Order order);
}

// Implementações (Spring Beans)
@Component("EXPRESS")
public class ExpressShipping implements ShippingStrategy {
    public BigDecimal calculate(Order order) {  // CC = 2
        return order.getWeight() > 10
            ? BigDecimal.valueOf(50)
            : BigDecimal.valueOf(25);
    }
}

@Component("STANDARD")
public class StandardShipping implements ShippingStrategy {
    public BigDecimal calculate(Order order) {  // CC = 3
        return switch (order.getRegion()) {
            case "SP" -> BigDecimal.valueOf(10);
            case "RJ" -> BigDecimal.valueOf(15);
            default   -> BigDecimal.valueOf(20);
        };
    }
}

// Serviço principal
@Service
public class ShippingService {
    private final Map<String, ShippingStrategy> strategies;
    
    public ShippingService(Map<String, ShippingStrategy> strategies) {
        this.strategies = strategies;
    }
    
    public BigDecimal calculateShipping(Order order) {  // CC = 2
        ShippingStrategy strategy = strategies.get(order.getShippingType().name());
        if (strategy == null) throw new IllegalArgumentException("Tipo inválido");
        return strategy.calculate(order);
    }
}
```

---

## 4. Chain of Responsibility (validações)

**Antes** — CC = 10+ em um único validador:
```java
public ValidationResult validate(Order order) {
    if (order.getTotal() <= 0) return ValidationResult.error("Total inválido");
    if (order.getItems().isEmpty()) return ValidationResult.error("Sem itens");
    if (order.getCustomer() == null) return ValidationResult.error("Sem cliente");
    if (!order.getCustomer().isActive()) return ValidationResult.error("Cliente inativo");
    if (order.getPaymentMethod() == null) return ValidationResult.error("Sem pagamento");
    // ...mais 5 validações
}
```

**Depois** — cada handler com CC = 2:
```java
public abstract class OrderValidator {
    private OrderValidator next;
    
    public OrderValidator setNext(OrderValidator next) {
        this.next = next;
        return next;
    }
    
    public ValidationResult validate(Order order) {
        ValidationResult result = doValidate(order);
        if (!result.isValid() || next == null) return result;
        return next.validate(order);
    }
    
    protected abstract ValidationResult doValidate(Order order);
}

@Component
public class TotalValidator extends OrderValidator {
    protected ValidationResult doValidate(Order order) {  // CC = 2
        return order.getTotal() <= 0
            ? ValidationResult.error("Total inválido")
            : ValidationResult.ok();
    }
}

// Configuração no @Service
@Service
public class OrderValidationService {
    public ValidationResult validate(Order order) {
        OrderValidator chain = new TotalValidator();
        chain.setNext(new ItemsValidator())
             .setNext(new CustomerValidator())
             .setNext(new PaymentValidator());
        return chain.validate(order);
    }
}
```

---

## 5. Specification Pattern (@Query complexo)

Para repositórios Spring Data com múltiplos filtros opcionais:

```java
// Antes: método de serviço com CC = 8+
public List<Order> findOrders(String status, LocalDate from, 
                               LocalDate to, String customerId) {
    if (status != null && from != null && to != null && customerId != null) {
        return repo.findByStatusAndDateBetweenAndCustomerId(...);
    } else if (status != null && from != null) {
        // ...
    }
    // ...vira um pesadelo
}

// Depois: Specification (CC = 1 no serviço)
public class OrderSpecifications {
    
    public static Specification<Order> hasStatus(String status) {
        return (root, query, cb) -> status == null ? null
            : cb.equal(root.get("status"), status);
    }
    
    public static Specification<Order> betweenDates(LocalDate from, LocalDate to) {
        return (root, query, cb) -> from == null ? null
            : cb.between(root.get("createdAt"), from, to);
    }
    
    public static Specification<Order> forCustomer(String customerId) {
        return (root, query, cb) -> customerId == null ? null
            : cb.equal(root.get("customerId"), customerId);
    }
}

@Service
public class OrderService {
    public List<Order> findOrders(String status, LocalDate from,
                                   LocalDate to, String customerId) {  // CC = 1
        return repo.findAll(
            where(hasStatus(status))
                .and(betweenDates(from, to))
                .and(forCustomer(customerId))
        );
    }
}
```
