package com.sistema_contabilidade.relatorio.dto;

import java.math.BigDecimal;
import java.util.List;

public record RelatorioFinanceiroResponse(
    BigDecimal receitasFinanceiras,
    BigDecimal receitasEstimaveis,
    BigDecimal totalReceitas,
    BigDecimal despesasConsideradas,
    BigDecimal despesasAdvocaciaContabilidade,
    BigDecimal totalDespesas,
    BigDecimal saldoFinal,
    List<RelatorioItemDto> receitas,
    List<RelatorioItemDto> despesas) {

  public RelatorioFinanceiroResponse {
    receitasFinanceiras = receitasFinanceiras == null ? BigDecimal.ZERO : receitasFinanceiras;
    receitasEstimaveis = receitasEstimaveis == null ? BigDecimal.ZERO : receitasEstimaveis;
    totalReceitas = totalReceitas == null ? BigDecimal.ZERO : totalReceitas;
    despesasConsideradas = despesasConsideradas == null ? BigDecimal.ZERO : despesasConsideradas;
    despesasAdvocaciaContabilidade =
        despesasAdvocaciaContabilidade == null ? BigDecimal.ZERO : despesasAdvocaciaContabilidade;
    totalDespesas = totalDespesas == null ? BigDecimal.ZERO : totalDespesas;
    saldoFinal = saldoFinal == null ? BigDecimal.ZERO : saldoFinal;
    receitas = receitas == null ? List.of() : List.copyOf(receitas);
    despesas = despesas == null ? List.of() : List.copyOf(despesas);
  }
}
