---
name: spring-query-monitor
description: >
  Implement a middleware/interceptor for Spring Boot + MySQL (or any JPA/Hibernate stack) that
  counts the number of SQL queries executed per HTTP request and raises an alert (log warning,
  custom header, or exception) when the count exceeds a configurable threshold (default 15).
  Use this skill whenever the user wants to: detect N+1 query problems, audit query counts per
  endpoint, add query-count middleware to a Spring Boot project, monitor Hibernate/JPA query
  performance, or review over-querying issues in a Java backend. Triggers on phrases like
  "count queries per request", "query threshold alert", "N+1 detection Spring", "middleware
  query monitor", "Hibernate query counter", or any mention of auditing SQL queries in Spring Boot.
---

# Spring Query Monitor Skill

Implement a **query-count middleware** for Spring Boot + JPA/Hibernate that:

1. Intercepts every HTTP request via a `HandlerInterceptor`
2. Hooks into Hibernate's `StatementInspector` (or a DataSource proxy) to count SQL statements
3. After the request, checks if count > threshold (default **15**)
4. If exceeded → logs a `WARN` with endpoint + count + stack hint, and optionally adds an `X-Query-Count` response header

---

## Architecture overview

```
HTTP Request
    │
    ▼
QueryCountInterceptor (HandlerInterceptor)
    │  preHandle  → resets ThreadLocal counter
    │  afterCompletion → reads counter, fires alert if > threshold
    │
    ├── QueryCountContext  (ThreadLocal<AtomicInteger>)
    │
    └── QueryCountStatementInspector  (implements StatementInspector)
            │  inspect(sql) → increments counter
            └── registered via Hibernate property:
                hibernate.session_factory.statement_inspector
```

> **Why StatementInspector and not P6Spy/datasource-proxy?**  
> `StatementInspector` is built into Hibernate (no extra dependency), works per-session
> (thread-bound), and has zero overhead in production when logging is disabled.
> If the user needs full SQL logging or works with non-Hibernate JDBC, see the
> **datasource-proxy alternative** in `references/datasource-proxy-alternative.md`.

---

## Step-by-step implementation

### 1. QueryCountContext — thread-local counter

```java
// src/main/java/com/example/querymonitor/QueryCountContext.java
package com.example.querymonitor;

import java.util.concurrent.atomic.AtomicInteger;

public final class QueryCountContext {

    private static final ThreadLocal<AtomicInteger> COUNTER =
            ThreadLocal.withInitial(AtomicInteger::new);

    private QueryCountContext() {}

    public static void reset() {
        COUNTER.get().set(0);
    }

    public static int increment() {
        return COUNTER.get().incrementAndGet();
    }

    public static int get() {
        return COUNTER.get().get();
    }

    /** Always call in afterCompletion to avoid memory leaks in thread pools. */
    public static void clear() {
        COUNTER.remove();
    }
}
```

---

### 2. QueryCountStatementInspector — Hibernate hook

```java
// src/main/java/com/example/querymonitor/QueryCountStatementInspector.java
package com.example.querymonitor;

import org.hibernate.resource.jdbc.spi.StatementInspector;

public class QueryCountStatementInspector implements StatementInspector {

    @Override
    public String inspect(String sql) {
        QueryCountContext.increment();
        return sql; // return unchanged SQL
    }
}
```

Register it in `application.properties` (or `application.yml`):

```properties
# application.properties
spring.jpa.properties.hibernate.session_factory.statement_inspector=\
  com.example.querymonitor.QueryCountStatementInspector
```

```yaml
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        session_factory:
          statement_inspector: com.example.querymonitor.QueryCountStatementInspector
```

---

### 3. QueryCountInterceptor — request wrapper

```java
// src/main/java/com/example/querymonitor/QueryCountInterceptor.java
package com.example.querymonitor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class QueryCountInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(QueryCountInterceptor.class);

    @Value("${query.monitor.threshold:15}")
    private int threshold;

    @Value("${query.monitor.add-response-header:true}")
    private boolean addResponseHeader;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        QueryCountContext.reset();
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        int count = QueryCountContext.get();

        if (addResponseHeader) {
            response.setHeader("X-Query-Count", String.valueOf(count));
        }

        if (count > threshold) {
            log.warn(
                "⚠️  QUERY REVIEW NEEDED — {} {} executed {} queries (threshold: {}). " +
                "Consider using JOIN FETCH, @EntityGraph, or a DTO projection.",
                request.getMethod(),
                request.getRequestURI(),
                count,
                threshold
            );
        } else {
            log.debug("{} {} → {} queries", request.getMethod(), request.getRequestURI(), count);
        }

        QueryCountContext.clear(); // prevent ThreadLocal leak
    }
}
```

---

### 4. WebMvcConfig — register the interceptor

```java
// src/main/java/com/example/querymonitor/WebMvcConfig.java
package com.example.querymonitor;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final QueryCountInterceptor queryCountInterceptor;

    public WebMvcConfig(QueryCountInterceptor queryCountInterceptor) {
        this.queryCountInterceptor = queryCountInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(queryCountInterceptor)
                .addPathPatterns("/api/**")   // ← adjust to your URL pattern
                .excludePathPatterns("/actuator/**", "/health");
    }
}
```

---

### 5. Configuration properties

Add to `application.properties`:

```properties
# Query monitor settings
query.monitor.threshold=15
query.monitor.add-response-header=true

# Optional: see all queries in dev
logging.level.com.example.querymonitor=DEBUG
```

---

## Optional: throw exception instead of logging

If the user wants to **fail fast** in tests (CI gate), replace the `log.warn` block with:

```java
if (count > threshold) {
    throw new QueryLimitExceededException(
        request.getRequestURI(), count, threshold);
}
```

```java
// QueryLimitExceededException.java
public class QueryLimitExceededException extends RuntimeException {
    public QueryLimitExceededException(String uri, int count, int threshold) {
        super(String.format(
            "Request %s executed %d queries, exceeding threshold of %d",
            uri, count, threshold));
    }
}
```

---

## Optional: Micrometer metric (Actuator / Prometheus)

```java
// In QueryCountInterceptor, inject MeterRegistry:
private final MeterRegistry meterRegistry;

// In afterCompletion, after calculating count:
meterRegistry.summary("http.query.count",
    "uri", request.getRequestURI(),
    "method", request.getMethod()
).record(count);
```

This lets you build a Grafana dashboard showing average/max queries per endpoint over time.

---

## File checklist

```
src/main/java/com/example/querymonitor/
├── QueryCountContext.java              ← ThreadLocal counter
├── QueryCountStatementInspector.java   ← Hibernate hook
├── QueryCountInterceptor.java          ← HTTP interceptor + alert logic
└── WebMvcConfig.java                   ← registers interceptor

src/main/resources/
└── application.properties              ← add hibernate + monitor props
```

---

## Common issues & solutions

| Symptom | Cause | Fix |
|---|---|---|
| Counter always 0 | `StatementInspector` not registered | Double-check the property key (full class name, no spaces) |
| Counts seem low | Queries from `@Transactional` services called before controller | Wrap `preHandle` in a filter instead (see `references/filter-alternative.md`) |
| Thread-safety issues with virtual threads | ThreadLocal behavior with Project Loom | Use `ScopedValue` or switch to datasource-proxy approach |
| Want to count per service layer, not per request | Different requirement | Use AOP `@Around` advice on `@Repository` or `@Service` methods |

---

## References

- `references/datasource-proxy-alternative.md` — using `datasource-proxy` lib for non-Hibernate JDBC
- `references/filter-alternative.md` — using `OncePerRequestFilter` instead of `HandlerInterceptor` (catches queries from filters/security chain too)
- `references/test-example.md` — how to write a Spring Boot integration test that asserts query count ≤ N
