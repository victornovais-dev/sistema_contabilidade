package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record ItemResponse(
    UUID id,
    BigDecimal valor,
    LocalDate data,
    LocalDateTime horarioCriacao,
    byte[] arquivoPdf,
    TipoItem tipo) {

  public ItemResponse {
    Objects.requireNonNull(arquivoPdf, "arquivoPdf");
    arquivoPdf = Arrays.copyOf(arquivoPdf, arquivoPdf.length);
  }

  public static ItemResponse from(Item item) {
    return new ItemResponse(
        item.getId(),
        item.getValor(),
        item.getData(),
        item.getHorarioCriacao(),
        item.getArquivoPdf(),
        item.getTipo());
  }

  @Override
  public byte[] arquivoPdf() {
    return Arrays.copyOf(arquivoPdf, arquivoPdf.length);
  }
}
