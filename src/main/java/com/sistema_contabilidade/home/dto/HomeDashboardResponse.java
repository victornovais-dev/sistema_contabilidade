package com.sistema_contabilidade.home.dto;

import java.math.BigDecimal;
import java.util.List;

public record HomeDashboardResponse(
    BigDecimal receitasFinanceiras,
    BigDecimal receitasEstimaveis,
    BigDecimal totalReceitas,
    BigDecimal totalDespesas,
    BigDecimal saldoFinal,
    List<HomeDashboardMonthResponse> graficoMensal,
    List<HomeLatestLaunchResponse> ultimosLancamentos) {

  public HomeDashboardResponse {
    receitasFinanceiras = receitasFinanceiras == null ? BigDecimal.ZERO : receitasFinanceiras;
    receitasEstimaveis = receitasEstimaveis == null ? BigDecimal.ZERO : receitasEstimaveis;
    totalReceitas = totalReceitas == null ? BigDecimal.ZERO : totalReceitas;
    totalDespesas = totalDespesas == null ? BigDecimal.ZERO : totalDespesas;
    saldoFinal = saldoFinal == null ? BigDecimal.ZERO : saldoFinal;
    graficoMensal = graficoMensal == null ? List.of() : List.copyOf(graficoMensal);
    ultimosLancamentos = ultimosLancamentos == null ? List.of() : List.copyOf(ultimosLancamentos);
  }
}
