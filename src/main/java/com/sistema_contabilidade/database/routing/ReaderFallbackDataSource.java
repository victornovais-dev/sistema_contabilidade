package com.sistema_contabilidade.database.routing;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

public class ReaderFallbackDataSource extends AbstractDataSource {

  static final String CONNECTION_FAILURE_METRIC = "app.db.reader.connection.failures";
  static final String CIRCUIT_STATE_METRIC = "app.db.reader.circuit.state";
  private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(5);

  private final DataSource readerDataSource;
  private final DataSource writerDataSource;
  private final Counter connectionFailureCounter;
  private final long retryDelayNanos;
  private final LongSupplier nanoTime;
  private final AtomicLong retryAtNanos = new AtomicLong();
  private final AtomicReference<CircuitState> circuitState =
      new AtomicReference<>(CircuitState.CLOSED);

  public ReaderFallbackDataSource(
      DataSource readerDataSource, DataSource writerDataSource, MeterRegistry meterRegistry) {
    this(readerDataSource, writerDataSource, meterRegistry, DEFAULT_RETRY_DELAY, System::nanoTime);
  }

  ReaderFallbackDataSource(
      DataSource readerDataSource,
      DataSource writerDataSource,
      MeterRegistry meterRegistry,
      Duration retryDelay,
      LongSupplier nanoTime) {
    this.readerDataSource = Objects.requireNonNull(readerDataSource);
    this.writerDataSource = Objects.requireNonNull(writerDataSource);
    this.connectionFailureCounter =
        Counter.builder(CONNECTION_FAILURE_METRIC)
            .description("Falhas ao adquirir conexao do reader")
            .register(meterRegistry);
    this.retryDelayNanos = retryDelay.toNanos();
    this.nanoTime = nanoTime;
    Gauge.builder(CIRCUIT_STATE_METRIC, this, ReaderFallbackDataSource::circuitStateValue)
        .description("Estado do circuito reader: 0 fechado, 1 aberto, 0.5 teste")
        .register(meterRegistry);
  }

  @Override
  public Connection getConnection() throws SQLException {
    return acquireConnection(readerDataSource::getConnection, writerDataSource::getConnection);
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return acquireConnection(
        () -> readerDataSource.getConnection(username, password),
        () -> writerDataSource.getConnection(username, password));
  }

  private Connection acquireConnection(
      ConnectionSupplier readerConnection, ConnectionSupplier writerConnection)
      throws SQLException {
    if (!shouldTryReader()) {
      return writerConnection.get();
    }

    try {
      Connection connection = readerConnection.get();
      circuitState.set(CircuitState.CLOSED);
      return connection;
    } catch (SQLException readerFailure) {
      connectionFailureCounter.increment();
      openCircuit();
      try {
        return writerConnection.get();
      } catch (SQLException writerFailure) {
        writerFailure.addSuppressed(readerFailure);
        throw writerFailure;
      }
    }
  }

  private boolean shouldTryReader() {
    CircuitState currentState = circuitState.get();
    if (currentState == CircuitState.CLOSED) {
      return true;
    }
    if (currentState == CircuitState.HALF_OPEN) {
      return false;
    }

    long now = nanoTime.getAsLong();
    if (now - retryAtNanos.get() < 0) {
      return false;
    }
    return circuitState.compareAndSet(CircuitState.OPEN, CircuitState.HALF_OPEN);
  }

  private void openCircuit() {
    retryAtNanos.set(nanoTime.getAsLong() + retryDelayNanos);
    circuitState.set(CircuitState.OPEN);
  }

  private double circuitStateValue() {
    return switch (circuitState.get()) {
      case CLOSED -> 0.0;
      case HALF_OPEN -> 0.5;
      case OPEN -> 1.0;
    };
  }

  @FunctionalInterface
  private interface ConnectionSupplier {
    Connection get() throws SQLException;
  }

  private enum CircuitState {
    CLOSED,
    HALF_OPEN,
    OPEN
  }
}
