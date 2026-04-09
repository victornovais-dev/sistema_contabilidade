package com.sistema_contabilidade.home.dto;

import java.math.BigDecimal;
import java.util.List;

public record HomeDashboardResponse(
    BigDecimal totalReceitas,
    BigDecimal totalDespesas,
    BigDecimal saldoFinal,
    List<HomeDashboardMonthResponse> graficoMensal,
    List<HomeLatestLaunchResponse> ultimosLancamentos) {

  public HomeDashboardResponse {
    totalReceitas = totalReceitas == null ? BigDecimal.ZERO : totalReceitas;
    totalDespesas = totalDespesas == null ? BigDecimal.ZERO : totalDespesas;
    saldoFinal = saldoFinal == null ? BigDecimal.ZERO : saldoFinal;
    graficoMensal = graficoMensal == null ? List.of() : List.copyOf(graficoMensal);
    ultimosLancamentos = ultimosLancamentos == null ? List.of() : List.copyOf(ultimosLancamentos);
  }
}
