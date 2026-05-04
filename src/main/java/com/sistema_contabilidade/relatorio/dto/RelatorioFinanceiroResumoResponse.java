package com.sistema_contabilidade.relatorio.dto;

import java.math.BigDecimal;

public record RelatorioFinanceiroResumoResponse(
    BigDecimal receitasFinanceiras,
    BigDecimal receitasEstimaveis,
    BigDecimal totalReceitas,
    BigDecimal despesasConsideradas,
    BigDecimal despesasAdvocaciaContabilidade,
    BigDecimal totalDespesas,
    BigDecimal despesasTotaisResumo,
    BigDecimal despesasCombustivel,
    BigDecimal despesasAlimentacao,
    BigDecimal despesasLocacaoVeiculos,
    BigDecimal tetoGastosCombustivel,
    BigDecimal tetoGastosAlimentacao,
    BigDecimal tetoGastosLocacaoVeiculos,
    BigDecimal saldoFinal,
    BigDecimal utilizadoRatio) {

  public RelatorioFinanceiroResumoResponse {
    receitasFinanceiras = zeroIfNull(receitasFinanceiras);
    receitasEstimaveis = zeroIfNull(receitasEstimaveis);
    totalReceitas = zeroIfNull(totalReceitas);
    despesasConsideradas = zeroIfNull(despesasConsideradas);
    despesasAdvocaciaContabilidade = zeroIfNull(despesasAdvocaciaContabilidade);
    totalDespesas = zeroIfNull(totalDespesas);
    despesasTotaisResumo = zeroIfNull(despesasTotaisResumo);
    despesasCombustivel = zeroIfNull(despesasCombustivel);
    despesasAlimentacao = zeroIfNull(despesasAlimentacao);
    despesasLocacaoVeiculos = zeroIfNull(despesasLocacaoVeiculos);
    tetoGastosCombustivel = zeroIfNull(tetoGastosCombustivel);
    tetoGastosAlimentacao = zeroIfNull(tetoGastosAlimentacao);
    tetoGastosLocacaoVeiculos = zeroIfNull(tetoGastosLocacaoVeiculos);
    saldoFinal = zeroIfNull(saldoFinal);
    utilizadoRatio = zeroIfNull(utilizadoRatio);
  }

  private static BigDecimal zeroIfNull(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
