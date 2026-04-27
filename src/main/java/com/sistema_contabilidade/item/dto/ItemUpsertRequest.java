package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.TipoItem;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record ItemUpsertRequest(
    @NotNull(message = "Valor e obrigatorio")
        @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
        @DecimalMax(value = "10000000.00", message = "Valor nao pode ultrapassar 10 milhoes")
        @Digits(
            integer = 8,
            fraction = 2,
            message = "Valor deve ter no maximo 8 digitos inteiros e 2 decimais")
        BigDecimal valor,
    @NotNull(message = "Data e obrigatoria") LocalDate data,
    @NotNull(message = "Horario de criacao e obrigatorio") LocalDateTime horarioCriacao,
    List<byte[]> arquivosPdf,
    List<String> nomesArquivos,
    @NotNull(message = "Tipo e obrigatorio") TipoItem tipo,
    @Size(max = 80, message = "Role deve ter no maximo 80 caracteres") String role,
    @Size(max = 120, message = "Descricao deve ter no maximo 120 caracteres") String descricao,
    @Size(max = 120, message = "Tipo de documento deve ter no maximo 120 caracteres")
        String tipoDocumento,
    @Size(max = 50, message = "Numero do documento deve ter no maximo 50 caracteres")
        @Pattern(regexp = "^\\d{1,50}$", message = "Numero do documento deve conter apenas numeros")
        String numeroDocumento,
    @Size(max = 150, message = "Razao social ou nome deve ter no maximo 150 caracteres")
        String razaoSocialNome,
    @Size(max = 32, message = "CNPJ ou CPF deve ter no maximo 32 caracteres") String cnpjCpf,
    @Size(max = 500, message = "Observacao deve ter no maximo 500 caracteres") String observacao) {

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
        && Objects.equals(role, other.role)
        && Objects.equals(descricao, other.descricao)
        && Objects.equals(tipoDocumento, other.tipoDocumento)
        && Objects.equals(numeroDocumento, other.numeroDocumento)
        && Objects.equals(razaoSocialNome, other.razaoSocialNome)
        && Objects.equals(cnpjCpf, other.cnpjCpf)
        && Objects.equals(observacao, other.observacao);
  }

  @Override
  public int hashCode() {
    return ItemRequestArraySupport.requestHashCode(this);
  }

  @Override
  public String toString() {
    return ItemRequestArraySupport.requestToString("ItemUpsertRequest", this);
  }
}
