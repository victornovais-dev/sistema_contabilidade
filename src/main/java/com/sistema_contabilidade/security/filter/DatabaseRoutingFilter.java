package com.sistema_contabilidade.security.filter;

import com.sistema_contabilidade.database.routing.DatabaseRoutingContext;
import com.sistema_contabilidade.database.service.StickyWriterService;
import com.sistema_contabilidade.monitoring.RequestMonitoringPathUtils;
import com.sistema_contabilidade.relatorio.service.RelatorioResumoCacheService;
import com.sistema_contabilidade.security.util.SecurityPaths;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class DatabaseRoutingFilter extends OncePerRequestFilter {

  private final StickyWriterService stickyWriterService;
  private final RelatorioResumoCacheService relatorioResumoCacheService;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String requestUri = request.getRequestURI();
    return RequestMonitoringPathUtils.isIgnoredPath(requestUri) || requestUri.startsWith("/src/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    UUID sessionId = validatedSessionId(request);
    try {
      configureRoute(request, sessionId);
      filterChain.doFilter(request, response);
      if (shouldMarkWriter(request, response, sessionId)) {
        stickyWriterService.markWriter(sessionId);
      }
      if (shouldInvalidateReportCache(request, response)) {
        relatorioResumoCacheService.invalidateAfterItemWrite();
      }
    } finally {
      DatabaseRoutingContext.clear();
    }
  }

  private void configureRoute(HttpServletRequest request, UUID sessionId) {
    if (!isReadMethod(request) || sessionId == null) {
      DatabaseRoutingContext.forceWriter();
      return;
    }
    if (stickyWriterService.requiresWriter(sessionId)) {
      DatabaseRoutingContext.forceWriterForSticky();
      return;
    }
    DatabaseRoutingContext.allowReader();
  }

  private boolean shouldMarkWriter(
      HttpServletRequest request, HttpServletResponse response, UUID sessionId) {
    return sessionId != null
        && isApiRequest(request)
        && isMutationMethod(request)
        && isSuccessful(response);
  }

  private boolean shouldInvalidateReportCache(
      HttpServletRequest request, HttpServletResponse response) {
    if (!isMutationMethod(request) || !isSuccessful(response)) {
      return false;
    }
    String itemsPrefix = request.getContextPath() + SecurityPaths.API_V1_PREFIX + "/itens";
    String requestUri = request.getRequestURI();
    return requestUri.equals(itemsPrefix) || requestUri.startsWith(itemsPrefix + "/");
  }

  private boolean isSuccessful(HttpServletResponse response) {
    return response.getStatus() >= HttpServletResponse.SC_OK
        && response.getStatus() < HttpServletResponse.SC_MULTIPLE_CHOICES;
  }

  private boolean isReadMethod(HttpServletRequest request) {
    return HttpMethod.GET.matches(request.getMethod())
        || HttpMethod.HEAD.matches(request.getMethod());
  }

  private boolean isMutationMethod(HttpServletRequest request) {
    return HttpMethod.POST.matches(request.getMethod())
        || HttpMethod.PUT.matches(request.getMethod())
        || HttpMethod.PATCH.matches(request.getMethod())
        || HttpMethod.DELETE.matches(request.getMethod());
  }

  private boolean isApiRequest(HttpServletRequest request) {
    String apiPrefix = request.getContextPath() + SecurityPaths.API_V1_PREFIX;
    String requestUri = request.getRequestURI();
    return requestUri.equals(apiPrefix) || requestUri.startsWith(apiPrefix + "/");
  }

  private UUID validatedSessionId(HttpServletRequest request) {
    Object sessionId = request.getAttribute(JwtAuthFilter.VALIDATED_SESSION_ID_ATTRIBUTE);
    return sessionId instanceof UUID uuid ? uuid : null;
  }
}
