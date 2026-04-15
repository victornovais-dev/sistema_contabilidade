package com.sistema_contabilidade.home.dto;

import java.math.BigDecimal;

public record HomeRevenueCategoryTotalRow(String descricao, BigDecimal total) {

  public HomeRevenueCategoryTotalRow {
    descricao = descricao == null ? "" : descricao;
    total = total == null ? BigDecimal.ZERO : total;
  }
}
