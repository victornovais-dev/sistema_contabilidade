package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.ContaOrigemPagamentoItem;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ItemPagamentoParcelaUpdateRequest(
    @Min(1) @Max(4) Integer numero,
    Boolean paga,
    ContaOrigemPagamentoItem contaOrigemPagamento,
    @DecimalMin(value = "0.01")
        @DecimalMax(value = "5000000.00")
        @Digits(integer = 13, fraction = 2)
        BigDecimal valorParcela,
    @Size(max = 10) List<byte[]> arquivosPdf,
    @Size(max = 10) List<@Size(max = 255) String> nomesArquivos,
    @Size(max = 10) List<UUID> arquivosRemovidos,
    Boolean removerArquivoLegado,
    byte[] arquivoPdf,
    @Size(max = 255) String nomeArquivo) {

  public ItemPagamentoParcelaUpdateRequest {
    arquivosPdf = ItemRequestArraySupport.copyOf(arquivosPdf);
    nomesArquivos = nomesArquivos == null ? List.of() : List.copyOf(nomesArquivos);
    arquivosRemovidos = arquivosRemovidos == null ? List.of() : List.copyOf(arquivosRemovidos);
    arquivoPdf = arquivoPdf == null ? new byte[0] : arquivoPdf.clone();
  }

  @Override
  public List<byte[]> arquivosPdf() {
    return ItemRequestArraySupport.copyOf(arquivosPdf);
  }

  @Override
  public List<String> nomesArquivos() {
    return List.copyOf(nomesArquivos);
  }

  @Override
  public List<UUID> arquivosRemovidos() {
    return List.copyOf(arquivosRemovidos);
  }

  @Override
  public byte[] arquivoPdf() {
    return arquivoPdf.clone();
  }

  @Override
  @SuppressWarnings("java:S6878")
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    return object instanceof ItemPagamentoParcelaUpdateRequest request
        && ItemRequestArraySupport.pagamentoParcelaEquals(this, request);
  }

  @Override
  public int hashCode() {
    return ItemRequestArraySupport.pagamentoParcelaHashCode(this);
  }

  @Override
  public String toString() {
    return ItemRequestArraySupport.pagamentoParcelaToString(this);
  }
}
