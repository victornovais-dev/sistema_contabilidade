package com.sistema_contabilidade.relatorio.dto;

import java.math.BigDecimal;
import java.util.List;

public record RelatorioFinanceiroResponse(
    BigDecimal totalReceitas,
    BigDecimal totalDespesas,
    BigDecimal saldoFinal,
    List<RelatorioItemDto> receitas,
    List<RelatorioItemDto> despesas) {

  public RelatorioFinanceiroResponse {
    receitas = receitas == null ? List.of() : List.copyOf(receitas);
    despesas = despesas == null ? List.of() : List.copyOf(despesas);
  }
}
