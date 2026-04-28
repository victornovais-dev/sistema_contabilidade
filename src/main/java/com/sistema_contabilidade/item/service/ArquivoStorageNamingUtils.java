package com.sistema_contabilidade.item.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class ArquivoStorageNamingUtils {

  private static final String PDF_EXTENSION = ".pdf";
  private static final char UNDERSCORE = '_';
  private static final char DOT = '.';
  private static final int MAX_NOME_ARQUIVO = 120;
  private static final int MAX_BASE_NOME_ARQUIVO = 72;
  private static final Set<String> WINDOWS_RESERVED_NAMES =
      Set.of(
          "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7",
          "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

  private ArquivoStorageNamingUtils() {}

  static String gerarNomeSanitizado(String nomeOriginal) {
    String uuid = UUID.randomUUID().toString();
    String baseNome = normalizarBaseNome(nomeOriginal);
    String nome = baseNome.isBlank() ? uuid + PDF_EXTENSION : uuid + "_" + baseNome + PDF_EXTENSION;
    return nome.length() <= MAX_NOME_ARQUIVO ? nome : nome.substring(0, MAX_NOME_ARQUIVO);
  }

  static String aplicarSuffix(String nome) {
    int dot = nome.lastIndexOf('.');
    String base = dot > 0 ? nome.substring(0, dot) : nome;
    String ext = dot > 0 ? nome.substring(dot) : "";
    return base + "_" + UUID.randomUUID() + ext;
  }

  private static String normalizarBaseNome(String nomeOriginal) {
    if (nomeOriginal == null || nomeOriginal.isBlank()) {
      return "";
    }
    String nome = nomeOriginal.replace("\0", "").trim();
    int lastSlash = Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\'));
    if (lastSlash >= 0) {
      nome = nome.substring(lastSlash + 1);
    }
    int lastDot = nome.lastIndexOf('.');
    if (lastDot > 0) {
      nome = nome.substring(0, lastDot);
    }
    nome = nome.replace(" ", "_").replace("-", "_");
    nome = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    nome = nome.replaceAll("[^A-Za-z0-9._]", "_");
    nome = colapsarUnderscores(nome);
    nome = apararCaracteresDeBorda(nome);
    if (nome.length() > MAX_BASE_NOME_ARQUIVO) {
      nome = nome.substring(0, MAX_BASE_NOME_ARQUIVO);
    }
    if (WINDOWS_RESERVED_NAMES.contains(nome.toUpperCase(Locale.ROOT))) {
      nome = "_" + nome;
    }
    return nome;
  }

  private static String colapsarUnderscores(String valor) {
    StringBuilder resultado = new StringBuilder(valor.length());
    boolean ultimoFoiUnderscore = false;
    for (int index = 0; index < valor.length(); index += 1) {
      char caractere = valor.charAt(index);
      if (caractere == UNDERSCORE) {
        if (!ultimoFoiUnderscore) {
          resultado.append(caractere);
        }
        ultimoFoiUnderscore = true;
        continue;
      }
      resultado.append(caractere);
      ultimoFoiUnderscore = false;
    }
    return resultado.toString();
  }

  private static String apararCaracteresDeBorda(String valor) {
    int inicio = 0;
    int fim = valor.length();
    while (inicio < fim && isBordaInvalida(valor.charAt(inicio))) {
      inicio += 1;
    }
    while (fim > inicio && isBordaInvalida(valor.charAt(fim - 1))) {
      fim -= 1;
    }
    return valor.substring(inicio, fim);
  }

  private static boolean isBordaInvalida(char caractere) {
    return caractere == UNDERSCORE || caractere == DOT;
  }
}
