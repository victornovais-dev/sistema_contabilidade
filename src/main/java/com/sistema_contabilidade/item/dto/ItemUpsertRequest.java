package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.TipoItem;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record ItemUpsertRequest(
    @NotNull(message = "Valor e obrigatorio") BigDecimal valor,
    @NotNull(message = "Data e obrigatoria") LocalDate data,
    @NotNull(message = "Horario de criacao e obrigatorio") LocalDateTime horarioCriacao,
    List<byte[]> arquivosPdf,
    List<String> nomesArquivos,
    @NotNull(message = "Tipo e obrigatorio") TipoItem tipo,
    String descricao,
    String razaoSocialNome,
    String cnpjCpf,
    String observacao) {

  public ItemUpsertRequest {
    if (arquivosPdf == null) {
      arquivosPdf = List.of();
    } else {
      arquivosPdf = ItemRequestArraySupport.copyOf(arquivosPdf);
    }
    if (nomesArquivos == null) {
      nomesArquivos = List.of();
    } else {
      nomesArquivos = List.copyOf(nomesArquivos);
    }
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
  @SuppressWarnings("java:S6878")
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ItemUpsertRequest other)) {
      return false;
    }
    return Objects.equals(valor, other.valor)
        && Objects.equals(data, other.data)
        && Objects.equals(horarioCriacao, other.horarioCriacao)
        && ItemRequestArraySupport.listsEqual(arquivosPdf, other.arquivosPdf)
        && Objects.equals(nomesArquivos, other.nomesArquivos)
        && tipo == other.tipo
        && Objects.equals(descricao, other.descricao)
        && Objects.equals(razaoSocialNome, other.razaoSocialNome)
        && Objects.equals(cnpjCpf, other.cnpjCpf)
        && Objects.equals(observacao, other.observacao);
  }

  @Override
  public int hashCode() {
    return ItemRequestArraySupport.requestHashCode(
        valor,
        data,
        horarioCriacao,
        arquivosPdf,
        nomesArquivos,
        tipo,
        descricao,
        razaoSocialNome,
        cnpjCpf,
        observacao);
  }

  @Override
  public String toString() {
    return ItemRequestArraySupport.requestToString(
        "ItemUpsertRequest",
        valor,
        data,
        horarioCriacao,
        arquivosPdf,
        nomesArquivos,
        tipo,
        descricao,
        razaoSocialNome,
        cnpjCpf,
        observacao);
  }
}
