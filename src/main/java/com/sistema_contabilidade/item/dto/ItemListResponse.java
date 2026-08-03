package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

public record ItemListResponse(
    UUID id,
    BigDecimal valor,
    LocalDate data,
    LocalDateTime horarioCriacao,
    TipoItem tipo,
    String role,
    String descricao,
    String razaoSocialNome,
    String cnpjCpfMascarado,
    boolean verificado,
    boolean temArquivos) {

  private static final int CPF_LENGTH = 11;
  private static final int CPF_VISIBLE_SUFFIX_START = 9;
  private static final int CNPJ_LENGTH = 14;
  private static final int CNPJ_VISIBLE_SUFFIX_START = 12;
  private static final Pattern MASKED_CPF_PATTERN =
      Pattern.compile("\\*\\*\\*\\.\\*\\*\\*\\.\\*\\*\\*-\\d{2}");
  private static final Pattern MASKED_CNPJ_PATTERN =
      Pattern.compile("\\*\\*\\.\\*\\*\\*\\.\\*\\*\\*/\\*\\*\\*\\*-\\d{2}");

  public ItemListResponse {
    cnpjCpfMascarado = mascararDocumento(cnpjCpfMascarado);
  }

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
        item.getCaminhoArquivoPdf() != null && !item.getCaminhoArquivoPdf().isBlank());
  }

  private static String mascararDocumento(String documento) {
    if (documento == null || documento.isBlank()) {
      return null;
    }
    if (MASKED_CPF_PATTERN.matcher(documento).matches()
        || MASKED_CNPJ_PATTERN.matcher(documento).matches()
        || "***".equals(documento)) {
      return documento;
    }
    String digitos = documento.replaceAll("\\D", "");
    if (digitos.length() == CPF_LENGTH) {
      return "***.***.***-" + digitos.substring(CPF_VISIBLE_SUFFIX_START);
    }
    if (digitos.length() == CNPJ_LENGTH) {
      return "**.***.***/****-" + digitos.substring(CNPJ_VISIBLE_SUFFIX_START);
    }
    return "***";
  }
}
