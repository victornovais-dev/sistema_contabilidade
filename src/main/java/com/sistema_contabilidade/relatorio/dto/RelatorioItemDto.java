package com.sistema_contabilidade.relatorio.dto;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record RelatorioItemDto(
    UUID id,
    TipoItem tipo,
    BigDecimal valor,
    LocalDate data,
    LocalDateTime horarioCriacao,
    String descricao) {

  public static RelatorioItemDto from(Item item) {
    return new RelatorioItemDto(
        item.getId(),
        item.getTipo(),
        item.getValor(),
        item.getData(),
        item.getHorarioCriacao(),
        item.getDescricao());
  }
}
