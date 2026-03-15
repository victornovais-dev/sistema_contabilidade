package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.TipoItem;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;

public record ItemUpdateRequest(
    @NotNull(message = "Valor e obrigatorio") BigDecimal valor,
    @NotNull(message = "Data e obrigatoria") LocalDate data,
    @NotNull(message = "Horario de criacao e obrigatorio") LocalDateTime horarioCriacao,
    @NotNull(message = "Arquivo PDF e obrigatorio") byte[] arquivoPdf,
    @NotNull(message = "Tipo e obrigatorio") TipoItem tipo) {

  public ItemUpdateRequest {
    Objects.requireNonNull(arquivoPdf, "arquivoPdf");
    arquivoPdf = Arrays.copyOf(arquivoPdf, arquivoPdf.length);
  }

  @Override
  public byte[] arquivoPdf() {
    return Arrays.copyOf(arquivoPdf, arquivoPdf.length);
  }
}
