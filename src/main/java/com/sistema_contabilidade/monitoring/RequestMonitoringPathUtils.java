package com.sistema_contabilidade.monitoring;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

public final class RequestMonitoringPathUtils {

  private static final String UNKNOWN_ROUTE = "UNKNOWN";

  private RequestMonitoringPathUtils() {}

  public static String resolveRoute(HttpServletRequest request) {
    Object bestMatchingPattern =
        request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (bestMatchingPattern instanceof String pattern && !pattern.isBlank()) {
      return pattern;
    }

    String requestUri = request.getRequestURI();
    return requestUri == null || requestUri.isBlank() ? UNKNOWN_ROUTE : requestUri;
  }

  public static boolean isIgnoredPath(String requestUri) {
    return requestUri.startsWith("/actuator")
        || requestUri.startsWith("/assets/")
        || requestUri.startsWith("/partials/")
        || requestUri.startsWith("/css/")
        || requestUri.startsWith("/js/")
        || requestUri.startsWith("/images/")
        || requestUri.startsWith("/webjars/")
        || "/favicon.ico".equals(requestUri);
  }
}
