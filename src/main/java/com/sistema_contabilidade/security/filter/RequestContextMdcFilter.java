package com.sistema_contabilidade.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestContextMdcFilter extends OncePerRequestFilter {

  private static final String REQUEST_ID_HEADER = "X-Request-ID";
  private static final String TRACE_ID_KEY = "trace.id";
  private static final String TRANSACTION_ID_KEY = "transaction.id";
  private static final String CLIENT_ADDRESS_KEY = "client.address";
  private static final String URL_PATH_KEY = "url.path";
  private static final String HTTP_METHOD_KEY = "http.request.method";
  private static final String USER_NAME_KEY = "user.name";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String traceId = resolveTraceId(request.getHeader(REQUEST_ID_HEADER));
    String transactionId = traceId.substring(0, 16);
    String clientAddress = resolveClientAddress(request);

    MDC.put(TRACE_ID_KEY, traceId);
    MDC.put(TRANSACTION_ID_KEY, transactionId);
    MDC.put(CLIENT_ADDRESS_KEY, clientAddress);
    MDC.put(URL_PATH_KEY, request.getRequestURI());
    MDC.put(HTTP_METHOD_KEY, request.getMethod());

    addAuthenticatedUserToMdc();
    response.setHeader(REQUEST_ID_HEADER, traceId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(USER_NAME_KEY);
      MDC.remove(HTTP_METHOD_KEY);
      MDC.remove(URL_PATH_KEY);
      MDC.remove(CLIENT_ADDRESS_KEY);
      MDC.remove(TRANSACTION_ID_KEY);
      MDC.remove(TRACE_ID_KEY);
    }
  }

  private void addAuthenticatedUserToMdc() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      return;
    }

    String username = authentication.getName();
    if (username != null && !username.isBlank()) {
      MDC.put(USER_NAME_KEY, username);
    }
  }

  private String resolveClientAddress(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  private String resolveTraceId(String traceIdHeader) {
    if (traceIdHeader == null || traceIdHeader.isBlank()) {
      return generateTraceId();
    }

    String normalized = traceIdHeader.trim().replace("-", "").toLowerCase(Locale.ROOT);
    return normalized.matches("^[0-9a-f]{32}$") ? normalized : generateTraceId();
  }

  private String generateTraceId() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
