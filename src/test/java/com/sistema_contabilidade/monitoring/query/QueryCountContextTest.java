package com.sistema_contabilidade.monitoring.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QueryCountContext unit tests")
class QueryCountContextTest {

  @AfterEach
  void tearDown() {
    QueryCountContext.clear();
  }

  @Test
  @DisplayName("Deve ignorar incremento quando contador nao estiver ativo")
  void deveIgnorarIncrementoQuandoContadorNaoEstiverAtivo() {
    assertEquals(0, QueryCountContext.increment());
    assertEquals(0, QueryCountContext.get());
  }

  @Test
  @DisplayName("Deve contar incrementos enquanto contexto estiver ativo")
  void deveContarIncrementosEnquantoContextoEstiverAtivo() {
    QueryCountContext.start();

    assertEquals(1, QueryCountContext.increment());
    assertEquals(2, QueryCountContext.increment());
    assertEquals(2, QueryCountContext.get());
  }

  @Test
  @DisplayName("Deve limpar contador")
  void deveLimparContador() {
    QueryCountContext.start();
    QueryCountContext.increment();

    QueryCountContext.clear();

    assertEquals(0, QueryCountContext.get());
  }
}
