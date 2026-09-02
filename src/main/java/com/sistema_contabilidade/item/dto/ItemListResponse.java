package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.ItemArquivo;
import com.sistema_contabilidade.item.model.ItemParcelaPagamento;
import com.sistema_contabilidade.item.model.ItemParcelaPagamentoArquivo;
import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ItemListResponse(
    UUID id,
    BigDecimal valor,
    LocalDate data,
    LocalDateTime horarioCriacao,
    TipoItem tipo,
    String role,
    String descricao,
    String razaoSocialNome,
    String cnpjCpf,
    boolean verificado,
    boolean temArquivos) {

  public static ItemListResponse from(Item item) {
    return new ItemListResponse(
        item.getId(),
        item.getValor(),
        item.getData(),
        item.getHorarioCriacao(),
        item.getTipo(),
        item.getRoleNome(),
        item.getDescricao(),
        item.getRazaoSocialNome(),
        item.getCnpjCpf(),
        item.isVerificado(),
        temArquivos(item));
  }

  private static boolean temArquivos(Item item) {
    return temCaminhoArquivo(item.getCaminhoArquivoPdf())
        || item.getArquivos().stream()
            .map(ItemArquivo::getCaminhoArquivoPdf)
            .anyMatch(ItemListResponse::temCaminhoArquivo)
        || item.getParcelasPagamento().stream().anyMatch(ItemListResponse::temArquivoPagamento);
  }

  private static boolean temArquivoPagamento(ItemParcelaPagamento parcela) {
    return temCaminhoArquivo(parcela.getCaminhoArquivoPdf())
        || parcela.getArquivosComprovantes().stream()
            .map(ItemParcelaPagamentoArquivo::getCaminhoArquivoPdf)
            .anyMatch(ItemListResponse::temCaminhoArquivo);
  }

  private static boolean temCaminhoArquivo(String caminhoArquivoPdf) {
    return caminhoArquivoPdf != null && !caminhoArquivoPdf.isBlank();
  }
}
