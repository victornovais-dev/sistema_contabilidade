# datasource-proxy Alternative

Use this approach when:
- You're **not using Hibernate** (plain JDBC, MyBatis, jOOQ)
- You want to capture queries fired from **filters or security chain** (before the MVC layer)
- You want **full SQL logging** with bind parameters

## Maven dependency

```xml
<dependency>
    <groupId>net.ttddyy</groupId>
    <artifactId>datasource-proxy</artifactId>
    <version>1.10</version>
</dependency>
```

## DataSource Bean

```java
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        HikariDataSource original = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class).build();

        return ProxyDataSourceBuilder.create(original)
                .name("QueryCountDS")
                .listener(new QueryCountListener())   // custom listener below
                .build();
    }
}
```

## QueryCountListener

```java
public class QueryCountListener implements QueryExecutionListener {

    @Override
    public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        // no-op
    }

    @Override
    public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        QueryCountContext.increment();
    }
}
```

The rest of the setup (interceptor, WebMvcConfig, properties) is identical to the
`StatementInspector` approach in the main SKILL.md.

## Trade-offs

| | StatementInspector | datasource-proxy |
|---|---|---|
| Extra dependency | No | Yes |
| Non-Hibernate support | No | Yes |
| Captures security/filter queries | No | Yes |
| Bind parameter logging | No | Yes |
| Production overhead | ~0 | Very low |
