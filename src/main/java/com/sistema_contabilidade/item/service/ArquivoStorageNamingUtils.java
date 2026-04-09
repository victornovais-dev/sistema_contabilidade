package com.sistema_contabilidade.item.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

final class ArquivoStorageNamingUtils {

  private static final String PDF_EXTENSION = ".pdf";
  private static final int MAX_NOME_ARQUIVO = 120;

  private ArquivoStorageNamingUtils() {}

  static String gerarNomeSanitizado(String nomeOriginal) {
    if (nomeOriginal == null || nomeOriginal.isBlank()) {
      return UUID.randomUUID() + PDF_EXTENSION;
    }
    String nome = nomeOriginal.trim().replace(" ", "_").replace("-", "_");
    nome = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    int lastSlash = Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\'));
    if (lastSlash >= 0) {
      nome = nome.substring(lastSlash + 1);
    }
    nome = nome.replaceAll("[^A-Za-z0-9._]", "_");
    if (!nome.toLowerCase(Locale.ROOT).endsWith(PDF_EXTENSION)) {
      nome = nome + PDF_EXTENSION;
    }
    if (nome.length() > MAX_NOME_ARQUIVO) {
      nome = nome.substring(0, MAX_NOME_ARQUIVO);
    }
    return nome.isBlank() ? UUID.randomUUID() + PDF_EXTENSION : nome;
  }

  static String aplicarSuffix(String nome) {
    int dot = nome.lastIndexOf('.');
    String base = dot > 0 ? nome.substring(0, dot) : nome;
    String ext = dot > 0 ? nome.substring(dot) : "";
    return base + "_" + UUID.randomUUID() + ext;
  }
}
