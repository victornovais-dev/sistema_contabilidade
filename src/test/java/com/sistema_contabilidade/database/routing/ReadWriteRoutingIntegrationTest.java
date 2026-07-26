package com.sistema_contabilidade.database.routing;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("Read/write routing integration tests")
class ReadWriteRoutingIntegrationTest {

  @Mock private DataSource writerDataSource;
  @Mock private DataSource readerDataSource;
  @Mock private Connection writerConnection;
  @Mock private Connection readerConnection;
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  @AfterEach
  void clearContext() {
    DatabaseRoutingContext.clear();
    meterRegistry.clear();
  }

  @Test
  @DisplayName("Deve adquirir reader dentro de transacao read-only elegivel")
  void deveAdquirirReaderDentroDeTransacaoReadOnlyElegivel() throws SQLException {
    when(readerDataSource.getConnection()).thenReturn(readerConnection);
    DataSource lazyDataSource = lazyRoutingDataSource();
    TransactionTemplate transaction = transaction(lazyDataSource, true);
    DatabaseRoutingContext.allowReader();

    transaction.executeWithoutResult(
        status -> createStatement(DataSourceUtils.getConnection(lazyDataSource)));

    verify(readerDataSource).getConnection();
    verify(writerDataSource, never()).getConnection();
  }

  @Test
  @DisplayName("Deve adquirir writer dentro de transacao de escrita")
  void deveAdquirirWriterDentroDeTransacaoDeEscrita() throws SQLException {
    DataSource lazyDataSource = lazyRoutingDataSource();
    TransactionTemplate transaction = transaction(lazyDataSource, false);
    DatabaseRoutingContext.allowReader();

    transaction.executeWithoutResult(
        status -> createStatement(DataSourceUtils.getConnection(lazyDataSource)));

    verify(writerDataSource).getConnection();
    verify(readerDataSource, never()).getConnection();
  }

  private DataSource lazyRoutingDataSource() throws SQLException {
    when(writerDataSource.getConnection()).thenReturn(writerConnection);
    when(writerConnection.getAutoCommit()).thenReturn(true);
    ReaderFallbackDataSource readerFallback =
        new ReaderFallbackDataSource(readerDataSource, writerDataSource, meterRegistry);
    ReadWriteRoutingDataSource routing =
        new ReadWriteRoutingDataSource(writerDataSource, readerFallback, meterRegistry);
    LazyConnectionDataSourceProxy lazyDataSource = new LazyConnectionDataSourceProxy(routing);
    lazyDataSource.checkDefaultConnectionProperties();
    clearInvocations(writerDataSource, writerConnection);
    return lazyDataSource;
  }

  private TransactionTemplate transaction(DataSource dataSource, boolean readOnly) {
    TransactionTemplate transaction =
        new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    transaction.setReadOnly(readOnly);
    return transaction;
  }

  private void createStatement(Connection connection) {
    try {
      connection.createStatement();
    } catch (SQLException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
