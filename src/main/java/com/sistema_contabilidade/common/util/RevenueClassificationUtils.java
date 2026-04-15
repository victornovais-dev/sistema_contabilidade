package com.sistema_contabilidade.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

public final class RevenueClassificationUtils {

  private static final String ESTIMAVEL = "ESTIMAVEL";
  private static final Set<String> FINANCIAL_REVENUE_DESCRIPTIONS =
      Set.of("CONTA FEFC", "CONTA FEFEC", "CONTA FP", "CONTA DC");

  private RevenueClassificationUtils() {}

  public static boolean isFinancialRevenue(String descricao) {
    return FINANCIAL_REVENUE_DESCRIPTIONS.contains(normalizeDescription(descricao));
  }

  public static boolean isEstimatedRevenue(String descricao) {
    return ESTIMAVEL.equals(normalizeDescription(descricao));
  }

  public static String normalizeDescription(String descricao) {
    if (descricao == null || descricao.isBlank()) {
      return "";
    }
    String normalized = Normalizer.normalize(descricao.trim(), Normalizer.Form.NFD);
    normalized = normalized.replaceAll("\\p{M}+", "");
    return normalized.toUpperCase(Locale.ROOT);
  }
}
