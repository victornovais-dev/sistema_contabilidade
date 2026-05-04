package com.sistema_contabilidade.relatorio.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RelatorioFinanceiroResumoResponse unit tests")
class RelatorioFinanceiroResumoResponseTest {

  @Test
  @DisplayName("Deve normalizar campos nulos para zero")
  void deveNormalizarCamposNulosParaZero() {
    RelatorioFinanceiroResumoResponse response =
        new RelatorioFinanceiroResumoResponse(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null);

    assertEquals(BigDecimal.ZERO, response.receitasFinanceiras());
    assertEquals(BigDecimal.ZERO, response.receitasEstimaveis());
    assertEquals(BigDecimal.ZERO, response.totalReceitas());
    assertEquals(BigDecimal.ZERO, response.despesasConsideradas());
    assertEquals(BigDecimal.ZERO, response.despesasAdvocaciaContabilidade());
    assertEquals(BigDecimal.ZERO, response.totalDespesas());
    assertEquals(BigDecimal.ZERO, response.despesasTotaisResumo());
    assertEquals(BigDecimal.ZERO, response.despesasCombustivel());
    assertEquals(BigDecimal.ZERO, response.despesasAlimentacao());
    assertEquals(BigDecimal.ZERO, response.despesasLocacaoVeiculos());
    assertEquals(BigDecimal.ZERO, response.tetoGastosCombustivel());
    assertEquals(BigDecimal.ZERO, response.tetoGastosAlimentacao());
    assertEquals(BigDecimal.ZERO, response.tetoGastosLocacaoVeiculos());
    assertEquals(BigDecimal.ZERO, response.saldoFinal());
    assertEquals(BigDecimal.ZERO, response.utilizadoRatio());
  }
}
