package com.sistema_contabilidade.security.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class SecurityUtils {

  private SecurityUtils() {}

  public static boolean safeEquals(String left, String right) {
    if (left == null || right == null) {
      return false;
    }
    return MessageDigest.isEqual(
        left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
  }
}
