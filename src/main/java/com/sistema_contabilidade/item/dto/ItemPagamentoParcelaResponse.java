package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.ContaOrigemPagamentoItem;
import com.sistema_contabilidade.item.model.ItemParcelaPagamento;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ItemPagamentoParcelaResponse(
    UUID id,
    Integer numero,
    BigDecimal valorParcela,
    boolean paga,
    ContaOrigemPagamentoItem contaOrigemPagamento,
    String nomeArquivoComprovante,
    boolean temArquivoComprovante,
    List<ItemPagamentoArquivoResponse> arquivosComprovantes) {

  public ItemPagamentoParcelaResponse {
    arquivosComprovantes =
        arquivosComprovantes == null ? List.of() : List.copyOf(arquivosComprovantes);
  }

  public static ItemPagamentoParcelaResponse from(
      ItemParcelaPagamento parcela,
      String nomeArquivoComprovante,
      List<ItemPagamentoArquivoResponse> arquivosComprovantes) {
    return new ItemPagamentoParcelaResponse(
        parcela.getId(),
        parcela.getNumero(),
        parcela.getValorParcela(),
        parcela.isPaga(),
        parcela.getContaOrigemPagamento(),
        nomeArquivoComprovante,
        nomeArquivoComprovante != null && !nomeArquivoComprovante.isBlank(),
        arquivosComprovantes);
  }
}
