package com.sistema_contabilidade.security.util;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

public final class RateLimitBucketResolver {

  private static final int IPV4_PART_COUNT = 4;
  private static final int MAX_IPV4_PART = 255;
  private static final Pattern DUPLICATE_SLASHES = Pattern.compile("/{2,}");
  private static final Pattern UUID_PATH_SEGMENT =
      Pattern.compile("/[\\da-fA-F]{8}-(?:[\\da-fA-F]{4}-){3}[\\da-fA-F]{12}(?=/|$)");
  private static final Pattern NUMERIC_PATH_SEGMENT = Pattern.compile("/\\d+(?=/|$)");
  private static final Pattern NUMERIC_IPV6 = Pattern.compile("[0-9a-f:.]+");

  private RateLimitBucketResolver() {}

  public static String resolve(HttpServletRequest request) {
    String source =
        normalizeIp(request.getRemoteAddr())
            + '\n'
            + request.getMethod().toUpperCase(Locale.ROOT)
            + '\n'
            + normalizeUri(request.getRequestURI());
    return sha256(source);
  }

  static String normalizeIp(String remoteAddress) {
    if (remoteAddress == null || remoteAddress.isBlank()) {
      return "unknown";
    }
    String candidate = remoteAddress.trim().toLowerCase(Locale.ROOT);
    int zoneSeparator = candidate.indexOf('%');
    if (zoneSeparator >= 0) {
      candidate = candidate.substring(0, zoneSeparator);
    }
    String ipv4 = normalizeIpv4(candidate);
    if (ipv4 != null) {
      return ipv4;
    }
    if (candidate.contains(":") && NUMERIC_IPV6.matcher(candidate).matches()) {
      try {
        return InetAddress.getByName(candidate).getHostAddress().toLowerCase(Locale.ROOT);
      } catch (UnknownHostException _) {
        return candidate;
      }
    }
    return candidate;
  }

  static String normalizeUri(String requestUri) {
    if (requestUri == null || requestUri.isBlank()) {
      return "/";
    }
    String normalized = requestUri.trim();
    int suffixIndex = firstSuffixIndex(normalized);
    if (suffixIndex >= 0) {
      normalized = normalized.substring(0, suffixIndex);
    }
    normalized = DUPLICATE_SLASHES.matcher(normalized).replaceAll("/");
    if (!normalized.startsWith("/")) {
      normalized = "/" + normalized;
    }
    while (normalized.length() > 1 && normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    normalized = UUID_PATH_SEGMENT.matcher(normalized).replaceAll("/{id}");
    return NUMERIC_PATH_SEGMENT.matcher(normalized).replaceAll("/{id}");
  }

  private static int firstSuffixIndex(String uri) {
    int queryIndex = uri.indexOf('?');
    int fragmentIndex = uri.indexOf('#');
    if (queryIndex < 0) {
      return fragmentIndex;
    }
    if (fragmentIndex < 0) {
      return queryIndex;
    }
    return Math.min(queryIndex, fragmentIndex);
  }

  private static String normalizeIpv4(String candidate) {
    String[] parts = candidate.split("\\.", -1);
    if (parts.length != IPV4_PART_COUNT) {
      return null;
    }
    StringBuilder normalized = new StringBuilder();
    for (String part : parts) {
      if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) {
        return null;
      }
      int value;
      try {
        value = Integer.parseInt(part);
      } catch (NumberFormatException _) {
        return null;
      }
      if (value > MAX_IPV4_PART) {
        return null;
      }
      if (!normalized.isEmpty()) {
        normalized.append('.');
      }
      normalized.append(value);
    }
    return normalized.toString();
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }
}
