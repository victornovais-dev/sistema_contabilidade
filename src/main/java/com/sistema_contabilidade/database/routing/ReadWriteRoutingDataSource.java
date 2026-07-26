package com.sistema_contabilidade.database.routing;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class ReadWriteRoutingDataSource extends AbstractRoutingDataSource {

  static final String ROUTE_METRIC = "app.db.route.total";

  private final Counter readerReadOnlyCounter;
  private final Counter writerRequestCounter;
  private final Counter writerTransactionCounter;

  public ReadWriteRoutingDataSource(
      DataSource writerDataSource, DataSource readerDataSource, MeterRegistry meterRegistry) {
    Map<Object, Object> targets = new HashMap<>();
    targets.put(DatabaseRoute.WRITER, writerDataSource);
    targets.put(DatabaseRoute.READER, readerDataSource);
    setTargetDataSources(targets);
    setDefaultTargetDataSource(writerDataSource);
    setLenientFallback(false);
    afterPropertiesSet();

    readerReadOnlyCounter = routeCounter(meterRegistry, DatabaseRoute.READER, "read_only");
    writerRequestCounter = routeCounter(meterRegistry, DatabaseRoute.WRITER, "request_forced");
    writerTransactionCounter =
        routeCounter(meterRegistry, DatabaseRoute.WRITER, "transaction_not_read_only");
  }

  @Override
  protected DatabaseRoute determineCurrentLookupKey() {
    if (!DatabaseRoutingContext.isReaderAllowed()) {
      writerRequestCounter.increment();
      return DatabaseRoute.WRITER;
    }
    if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
      writerTransactionCounter.increment();
      return DatabaseRoute.WRITER;
    }
    readerReadOnlyCounter.increment();
    return DatabaseRoute.READER;
  }

  private Counter routeCounter(MeterRegistry meterRegistry, DatabaseRoute route, String reason) {
    return Counter.builder(ROUTE_METRIC)
        .description("Conexoes selecionadas por rota de banco")
        .tag("route", route.name().toLowerCase(java.util.Locale.ROOT))
        .tag("reason", reason)
        .register(meterRegistry);
  }
}
