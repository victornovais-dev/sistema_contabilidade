package com.sistema_contabilidade.relatorio.dto;

import java.math.BigDecimal;

public record RelatorioSaldoContaResponse(String conta, BigDecimal saldo) {

  public RelatorioSaldoContaResponse {
    saldo = saldo == null ? BigDecimal.ZERO : saldo;
  }
}
