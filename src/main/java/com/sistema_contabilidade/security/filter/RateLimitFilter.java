package com.sistema_contabilidade.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

  private final int maxRequests;
  private final long windowSeconds;
  private final Map<String, Deque<Long>> requestsByClient = new ConcurrentHashMap<>();

  public RateLimitFilter(
      @Value("${app.security.rate-limit.max-requests:120}") int maxRequests,
      @Value("${app.security.rate-limit.window-seconds:60}") long windowSeconds) {
    this.maxRequests = maxRequests;
    this.windowSeconds = windowSeconds;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/v1/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String clientKey = resolveClientKey(request);
    long now = Instant.now().getEpochSecond();

    Deque<Long> window = requestsByClient.computeIfAbsent(clientKey, ignored -> new ArrayDeque<>());
    synchronized (window) {
      while (!window.isEmpty() && window.peekFirst() <= now - windowSeconds) {
        window.pollFirst();
      }
      if (window.size() >= maxRequests) {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response
            .getWriter()
            .write("{\"status\":429,\"message\":\"Limite de requisicoes excedido\"}");
        return;
      }
      window.addLast(now);
    }

    filterChain.doFilter(request, response);
  }

  private String resolveClientKey(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
