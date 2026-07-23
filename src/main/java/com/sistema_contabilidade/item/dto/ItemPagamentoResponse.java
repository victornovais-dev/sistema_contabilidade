package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.FormaPagamentoItem;
import java.math.BigDecimal;
import java.util.List;

public record ItemPagamentoResponse(
    FormaPagamentoItem formaPagamento,
    Integer quantidadeParcelas,
    BigDecimal totalPago,
    List<ItemPagamentoParcelaResponse> parcelas) {

  public ItemPagamentoResponse {
    parcelas = parcelas == null ? List.of() : List.copyOf(parcelas);
  }
}
