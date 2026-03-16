package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;

final class ItemRequestArraySupport {

  private ItemRequestArraySupport() {}

  static int requestHashCode(
      BigDecimal valor,
      LocalDate data,
      LocalDateTime horarioCriacao,
      byte[] arquivoPdf,
      TipoItem tipo) {
    int result = Objects.hash(valor, data, horarioCriacao, tipo);
    result = 31 * result + Arrays.hashCode(arquivoPdf);
    return result;
  }

  static String requestToString(
      String typeName,
      BigDecimal valor,
      LocalDate data,
      LocalDateTime horarioCriacao,
      byte[] arquivoPdf,
      TipoItem tipo) {
    return typeName
        + "[valor="
        + valor
        + ", data="
        + data
        + ", horarioCriacao="
        + horarioCriacao
        + ", arquivoPdf="
        + Arrays.toString(arquivoPdf)
        + ", tipo="
        + tipo
        + "]";
  }
}
