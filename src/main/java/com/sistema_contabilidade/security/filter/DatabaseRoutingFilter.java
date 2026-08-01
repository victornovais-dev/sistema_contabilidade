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
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseCookie;
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
        addSignedMarker(request, response, sessionId);
      }
      if (isSuccessfulLogout(request, response)) {
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            stickyWriterService.clearSignedMarkerCookie(isSecureRequest(request)).toString());
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
    if (stickyWriterService.requiresWriter(
        sessionId, resolveCookieValue(request, stickyWriterService.markerCookieName()))) {
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

  private void addSignedMarker(
      HttpServletRequest request, HttpServletResponse response, UUID sessionId) {
    Optional<ResponseCookie> marker =
        stickyWriterService.signedMarkerCookie(sessionId, isSecureRequest(request));
    marker.ifPresent(cookie -> response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString()));
  }

  private boolean isSuccessfulLogout(HttpServletRequest request, HttpServletResponse response) {
    String logoutPath = request.getContextPath() + SecurityPaths.AUTH_API_BASE + "/logout";
    return HttpMethod.POST.matches(request.getMethod())
        && logoutPath.equals(request.getRequestURI())
        && isSuccessful(response);
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

  private String resolveCookieValue(HttpServletRequest request, String cookieName) {
    if (request.getCookies() == null) {
      return null;
    }
    for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
      if (cookieName.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  private boolean isSecureRequest(HttpServletRequest request) {
    String forwardedProto = request.getHeader("X-Forwarded-Proto");
    return request.isSecure() || "https".equalsIgnoreCase(forwardedProto);
  }
}
