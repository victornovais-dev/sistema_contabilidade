# Integration Test Example

Use this pattern to assert that an endpoint does **not** exceed the query threshold.
This can be used as a **CI gate** to prevent regressions.

## Maven/Gradle dependency

Already included if you have `spring-boot-starter-test`.

## Test class

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class QueryCountIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/orders should execute ≤ 15 queries")
    void ordersEndpointShouldNotExceedQueryThreshold() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andReturn();

        String header = result.getResponse().getHeader("X-Query-Count");
        assertThat(header).isNotNull();

        int queryCount = Integer.parseInt(header);
        assertThat(queryCount)
                .as("GET /api/orders executed %d queries — review for N+1 problems", queryCount)
                .isLessThanOrEqualTo(15);
    }
}
```

## Using a lower per-test threshold

If you want stricter limits in tests (e.g., ≤ 5 queries for a simple endpoint):

```java
// Override the property just for this test class
@SpringBootTest(properties = "query.monitor.threshold=5")
class StrictQueryCountTest { ... }
```

## Failing fast (throw exception mode)

If `QueryLimitExceededException` is configured (see main SKILL.md), you don't need
to read the header — the test will simply fail with the exception message.

## Tips

- Use `@Sql` to pre-populate test data so counts are deterministic
- Run query-count tests with `@DirtiesContext` if your test modifies shared state
- Tag these tests with `@Tag("query-audit")` to run them separately in CI
