package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.Item;
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
    String caminhoArquivoPdf,
    TipoItem tipo,
    String role,
    String descricao,
    String razaoSocialNome,
    String cnpjCpf,
    String observacao,
    boolean verificado,
    boolean temArquivos) {

  public static ItemListResponse from(Item item) {
    return new ItemListResponse(
        item.getId(),
        item.getValor(),
        item.getData(),
        item.getHorarioCriacao(),
        item.getCaminhoArquivoPdf(),
        item.getTipo(),
        item.getRoleNome(),
        item.getDescricao(),
        item.getRazaoSocialNome(),
        item.getCnpjCpf(),
        item.getObservacao(),
        item.isVerificado(),
        item.getCaminhoArquivoPdf() != null && !item.getCaminhoArquivoPdf().isBlank());
  }
}
