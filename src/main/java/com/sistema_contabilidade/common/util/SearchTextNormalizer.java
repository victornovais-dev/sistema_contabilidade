package com.sistema_contabilidade.common.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SearchTextNormalizer {

  private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
  private static final Pattern NON_ALPHANUMERIC =
      Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private SearchTextNormalizer() {}

  public static String normalizeForSearch(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD);
    normalized = DIACRITICS.matcher(normalized).replaceAll("");
    normalized = NON_ALPHANUMERIC.matcher(normalized).replaceAll(" ");
    normalized = WHITESPACE.matcher(normalized).replaceAll(" ").trim();
    if (normalized.isBlank()) {
      return null;
    }

    return normalized.toUpperCase(Locale.ROOT);
  }

  public static List<String> tokenize(String value) {
    String normalized = normalizeForSearch(value);
    if (normalized == null) {
      return List.of();
    }

    return Arrays.stream(normalized.split(" "))
        .filter(token -> !token.isBlank())
        .distinct()
        .toList();
  }

  public static boolean allTokensHaveMinLength(String value, int minLength) {
    List<String> tokens = tokenize(value);
    if (tokens.isEmpty()) {
      return false;
    }

    return tokens.stream().allMatch(token -> token.length() >= minLength);
  }

  public static String toBooleanPrefixQuery(String value, int minLength) {
    List<String> tokens =
        tokenize(value).stream().filter(token -> token.length() >= minLength).toList();
    if (tokens.isEmpty()) {
      return null;
    }

    return tokens.stream()
        .map(token -> "+" + token + "*")
        .collect(java.util.stream.Collectors.joining(" "));
  }
}
