# OncePerRequestFilter Alternative

Use this approach instead of `HandlerInterceptor` when you need to **also count queries
executed by Spring Security filters** or other servlet filters that run before the MVC layer.

## Implementation

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class QueryCountFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(QueryCountFilter.class);

    @Value("${query.monitor.threshold:15}")
    private int threshold;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        QueryCountContext.reset();
        try {
            filterChain.doFilter(request, response);
        } finally {
            int count = QueryCountContext.get();
            response.setHeader("X-Query-Count", String.valueOf(count));

            if (count > threshold) {
                log.warn("⚠️  QUERY REVIEW NEEDED — {} {} → {} queries (threshold: {})",
                    request.getMethod(), request.getRequestURI(), count, threshold);
            }

            QueryCountContext.clear();
        }
    }
}
```

## When to use HandlerInterceptor vs Filter

| | HandlerInterceptor | OncePerRequestFilter |
|---|---|---|
| Catches security/filter queries | No | Yes |
| Has access to handler/controller info | Yes | No |
| Configuration | WebMvcConfigurer | Auto-detected as @Component |
| Runs after DispatcherServlet | Yes | No (wraps everything) |

For most business APIs where authentication is handled separately and queries originate
from controllers/services, `HandlerInterceptor` is simpler and recommended.
