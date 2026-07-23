package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.FormaPagamentoItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ItemPagamentoUpdateRequest(
    @NotNull FormaPagamentoItem formaPagamento,
    @Min(1) @Max(4) Integer quantidadeParcelas,
    @Valid @Size(min = 1, max = 4) List<ItemPagamentoParcelaUpdateRequest> parcelas) {

  public ItemPagamentoUpdateRequest {
    parcelas = parcelas == null ? List.of() : List.copyOf(parcelas);
  }

  @Override
  public List<ItemPagamentoParcelaUpdateRequest> parcelas() {
    return List.copyOf(parcelas);
  }
}
