package com.sistema_contabilidade.database.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DisplayName("ReadWriteRoutingDataSource unit tests")
class ReadWriteRoutingDataSourceTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final ReadWriteRoutingDataSource routingDataSource =
      new ReadWriteRoutingDataSource(mock(DataSource.class), mock(DataSource.class), meterRegistry);

  @AfterEach
  void clearContexts() {
    DatabaseRoutingContext.clear();
    TransactionSynchronizationManager.clear();
    meterRegistry.close();
  }

  @Test
  @DisplayName("Deve usar writer quando request nao permite reader")
  void deveUsarWriterQuandoRequestNaoPermiteReader() {
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

    assertEquals(DatabaseRoute.WRITER, routingDataSource.determineCurrentLookupKey());
    assertEquals(
        1.0,
        meterRegistry
            .find(ReadWriteRoutingDataSource.ROUTE_METRIC)
            .tags("route", "writer", "reason", "request_forced")
            .counter()
            .count());
  }

  @Test
  @DisplayName("Deve usar writer quando transacao nao e read-only")
  void deveUsarWriterQuandoTransacaoNaoEReadOnly() {
    DatabaseRoutingContext.allowReader();

    assertEquals(DatabaseRoute.WRITER, routingDataSource.determineCurrentLookupKey());
    assertEquals(
        1.0,
        meterRegistry
            .find(ReadWriteRoutingDataSource.ROUTE_METRIC)
            .tags("route", "writer", "reason", "transaction_not_read_only")
            .counter()
            .count());
  }

  @Test
  @DisplayName("Deve usar reader quando request permite e transacao e read-only")
  void deveUsarReaderQuandoRequestPermiteETransacaoEReadOnly() {
    DatabaseRoutingContext.allowReader();
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

    assertEquals(DatabaseRoute.READER, routingDataSource.determineCurrentLookupKey());
    assertEquals(
        1.0,
        meterRegistry
            .find(ReadWriteRoutingDataSource.ROUTE_METRIC)
            .tags("route", "reader", "reason", "read_only")
            .counter()
            .count());
  }
}
