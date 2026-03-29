package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

final class ItemRequestArraySupport {

  private ItemRequestArraySupport() {}

  static List<byte[]> copyOf(List<byte[]> arquivosPdf) {
    if (arquivosPdf == null || arquivosPdf.isEmpty()) {
      return List.of();
    }
    return arquivosPdf.stream()
        .filter(Objects::nonNull)
        .map(array -> Arrays.copyOf(array, array.length))
        .toList();
  }

  static boolean listsEqual(List<byte[]> left, List<byte[]> right) {
    if (Objects.equals(left, right)) {
      return true;
    }
    if (left == null || right == null || left.size() != right.size()) {
      return false;
    }
    for (int i = 0; i < left.size(); i += 1) {
      if (!Arrays.equals(left.get(i), right.get(i))) {
        return false;
      }
    }
    return true;
  }

  static int listHashCode(List<byte[]> arquivosPdf) {
    if (arquivosPdf == null || arquivosPdf.isEmpty()) {
      return 0;
    }
    int result = 1;
    for (byte[] entry : arquivosPdf) {
      result = 31 * result + Arrays.hashCode(entry);
    }
    return result;
  }

  static String listToString(List<byte[]> arquivosPdf) {
    if (arquivosPdf == null || arquivosPdf.isEmpty()) {
      return "[]";
    }
    StringBuilder builder = new StringBuilder("[");
    for (int i = 0; i < arquivosPdf.size(); i += 1) {
      if (i > 0) {
        builder.append(", ");
      }
      builder.append(Arrays.toString(arquivosPdf.get(i)));
    }
    builder.append("]");
    return builder.toString();
  }

  static int requestHashCode(
      BigDecimal valor,
      LocalDate data,
      LocalDateTime horarioCriacao,
      List<byte[]> arquivosPdf,
      List<String> nomesArquivos,
      TipoItem tipo,
      String descricao,
      String razaoSocialNome,
      String cnpjCpf,
      String observacao) {
    int result =
        Objects.hash(
            valor,
            data,
            horarioCriacao,
            tipo,
            descricao,
            razaoSocialNome,
            cnpjCpf,
            observacao,
            nomesArquivos);
    result = 31 * result + listHashCode(arquivosPdf);
    return result;
  }

  static String requestToString(
      String typeName,
      BigDecimal valor,
      LocalDate data,
      LocalDateTime horarioCriacao,
      List<byte[]> arquivosPdf,
      List<String> nomesArquivos,
      TipoItem tipo,
      String descricao,
      String razaoSocialNome,
      String cnpjCpf,
      String observacao) {
    return typeName
        + "[valor="
        + valor
        + ", data="
        + data
        + ", horarioCriacao="
        + horarioCriacao
        + ", arquivosPdf="
        + listToString(arquivosPdf)
        + ", nomesArquivos="
        + nomesArquivos
        + ", tipo="
        + tipo
        + ", descricao="
        + descricao
        + ", razaoSocialNome="
        + razaoSocialNome
        + ", cnpjCpf="
        + cnpjCpf
        + ", observacao="
        + observacao
        + "]";
  }
}
