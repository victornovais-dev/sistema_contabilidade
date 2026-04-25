package com.sistema_contabilidade.monitoring.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QueryCountStatementInspector unit tests")
class QueryCountStatementInspectorTest {

  private final QueryCountStatementInspector inspector = new QueryCountStatementInspector();

  @AfterEach
  void tearDown() {
    QueryCountContext.clear();
  }

  @Test
  @DisplayName("Deve retornar SQL sem alteracoes e incrementar contador")
  void deveRetornarSqlSemAlteracoesEIncrementarContador() {
    QueryCountContext.start();
    String sql = "select * from usuario";

    String inspectedSql = inspector.inspect(sql);

    assertEquals(sql, inspectedSql);
    assertEquals(1, QueryCountContext.get());
  }

  @Test
  @DisplayName("Deve manter SQL intacto mesmo quando contador nao estiver ativo")
  void deveManterSqlIntactoMesmoQuandoContadorNaoEstiverAtivo() {
    String sql = "select * from item";

    String inspectedSql = inspector.inspect(sql);

    assertEquals(sql, inspectedSql);
    assertEquals(0, QueryCountContext.get());
  }
}
