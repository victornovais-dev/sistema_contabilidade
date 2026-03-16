package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ItemResponse(
    UUID id,
    BigDecimal valor,
    LocalDate data,
    LocalDateTime horarioCriacao,
    String caminhoArquivoPdf,
    TipoItem tipo) {

  public static ItemResponse from(Item item) {
    return new ItemResponse(
        item.getId(),
        item.getValor(),
        item.getData(),
        item.getHorarioCriacao(),
        item.getCaminhoArquivoPdf(),
        item.getTipo());
  }
}
