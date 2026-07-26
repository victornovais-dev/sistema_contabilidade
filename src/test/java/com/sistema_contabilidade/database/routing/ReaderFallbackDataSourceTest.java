package com.sistema_contabilidade.database.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReaderFallbackDataSource unit tests")
class ReaderFallbackDataSourceTest {

  @Mock private DataSource readerDataSource;
  @Mock private DataSource writerDataSource;
  @Mock private Connection readerConnection;
  @Mock private Connection writerConnection;

  @Test
  @DisplayName("Deve retornar conexao reader quando aquisicao funciona")
  void deveRetornarConexaoReaderQuandoAquisicaoFunciona() throws SQLException {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    when(readerDataSource.getConnection()).thenReturn(readerConnection);
    ReaderFallbackDataSource dataSource = newDataSource(registry, new MutableNanoTime());

    assertSame(readerConnection, dataSource.getConnection());
    verify(writerDataSource, never()).getConnection();
    assertEquals(0.0, registry.get(ReaderFallbackDataSource.CIRCUIT_STATE_METRIC).gauge().value());
  }

  @Test
  @DisplayName("Deve usar writer e abrir circuito por cinco segundos")
  void deveUsarWriterEAbrirCircuitoPorCincoSegundos() throws SQLException {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    MutableNanoTime nanoTime = new MutableNanoTime();
    when(readerDataSource.getConnection())
        .thenThrow(new SQLException("reader indisponivel"))
        .thenReturn(readerConnection);
    when(writerDataSource.getConnection()).thenReturn(writerConnection);
    ReaderFallbackDataSource dataSource = newDataSource(registry, nanoTime);

    assertSame(writerConnection, dataSource.getConnection());
    assertSame(writerConnection, dataSource.getConnection());
    verify(readerDataSource, times(1)).getConnection();
    assertEquals(1.0, registry.get(ReaderFallbackDataSource.CIRCUIT_STATE_METRIC).gauge().value());

    nanoTime.advance(Duration.ofSeconds(5));

    assertSame(readerConnection, dataSource.getConnection());
    verify(readerDataSource, times(2)).getConnection();
    assertEquals(
        1.0, registry.get(ReaderFallbackDataSource.CONNECTION_FAILURE_METRIC).counter().count());
    assertEquals(0.0, registry.get(ReaderFallbackDataSource.CIRCUIT_STATE_METRIC).gauge().value());
  }

  @Test
  @DisplayName("Nao deve repetir query que falha depois da conexao adquirida")
  void naoDeveRepetirQueryQueFalhaDepoisDaConexaoAdquirida() throws SQLException {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    when(readerDataSource.getConnection()).thenReturn(readerConnection);
    when(readerConnection.createStatement()).thenThrow(new SQLException("query falhou"));
    ReaderFallbackDataSource dataSource = newDataSource(registry, new MutableNanoTime());

    Connection connection = dataSource.getConnection();

    assertThrows(SQLException.class, connection::createStatement);
    verifyNoInteractions(writerDataSource);
    assertEquals(
        0.0, registry.get(ReaderFallbackDataSource.CONNECTION_FAILURE_METRIC).counter().count());
  }

  @Test
  @DisplayName("Deve preservar credenciais ao fazer fallback")
  void devePreservarCredenciaisAoFazerFallback() throws SQLException {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    when(readerDataSource.getConnection("user", "password"))
        .thenThrow(new SQLException("reader indisponivel"));
    when(writerDataSource.getConnection("user", "password")).thenReturn(writerConnection);
    ReaderFallbackDataSource dataSource = newDataSource(registry, new MutableNanoTime());

    assertSame(writerConnection, dataSource.getConnection("user", "password"));
  }

  private ReaderFallbackDataSource newDataSource(
      SimpleMeterRegistry registry, MutableNanoTime nanoTime) {
    return new ReaderFallbackDataSource(
        readerDataSource, writerDataSource, registry, Duration.ofSeconds(5), nanoTime);
  }

  private static final class MutableNanoTime implements LongSupplier {

    private long value;

    @Override
    public long getAsLong() {
      return value;
    }

    private void advance(Duration duration) {
      value += TimeUnit.MILLISECONDS.toNanos(duration.toMillis());
    }
  }
}
