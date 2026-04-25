package com.sistema_contabilidade.monitoring.query;

import org.hibernate.resource.jdbc.spi.StatementInspector;

public class QueryCountStatementInspector implements StatementInspector {

  private static final long serialVersionUID = 1L;

  @Override
  public String inspect(String sql) {
    QueryCountContext.increment();
    return sql;
  }
}
