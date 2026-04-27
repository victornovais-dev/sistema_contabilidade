package com.sistema_contabilidade.security.filter;

import com.sistema_contabilidade.security.service.AdminRouteService;
import com.sistema_contabilidade.security.service.RequestFingerprintService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class AdminRouteFilter extends OncePerRequestFilter {

  private final AdminRouteService adminRouteService;
  private final RequestFingerprintService requestFingerprintService;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return adminRouteService.isStaticResource(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestPath = request.getRequestURI();
    Optional<String> internalPath = adminRouteService.resolveInternalPath(requestPath);
    if (internalPath.isPresent()) {
      String clientIp = requestFingerprintService.resolveClientIp(request);
      if (!adminRouteService.isIpAllowed(clientIp)) {
        if (log.isWarnEnabled()) {
          log.warn("Tentativa negada de acesso admin secreto por IP nao permitido: {}", clientIp);
        }
        sendNotFound(requestPath, response);
        return;
      }
      filterChain.doFilter(new RewrittenPathRequestWrapper(request, internalPath.get()), response);
      return;
    }

    if (adminRouteService.isLegacyAdminPath(requestPath)) {
      if (log.isWarnEnabled()) {
        log.warn(
            "Tentativa de acesso a rota admin legada detectada. path={} ip={}",
            requestPath,
            requestFingerprintService.resolveClientIp(request));
      }
      sendNotFound(requestPath, response);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private void sendNotFound(String requestPath, HttpServletResponse response) throws IOException {
    if (requestPath.startsWith("/api/")) {
      response.setStatus(HttpStatus.NOT_FOUND.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write("{\"status\":404,\"message\":\"Recurso nao encontrado\"}");
      return;
    }
    response.sendRedirect("/404");
  }

  private static final class RewrittenPathRequestWrapper extends HttpServletRequestWrapper {

    private final String rewrittenPath;

    private RewrittenPathRequestWrapper(HttpServletRequest request, String rewrittenPath) {
      super(request);
      this.rewrittenPath = rewrittenPath;
    }

    @Override
    public String getRequestURI() {
      return rewrittenPath;
    }

    @Override
    public String getServletPath() {
      return rewrittenPath;
    }

    @Override
    public StringBuffer getRequestURL() {
      HttpServletRequest request = (HttpServletRequest) getRequest();
      StringBuffer requestUrl =
          new StringBuffer(request.getScheme()).append("://").append(request.getServerName());
      if (request.getServerPort() > 0) {
        requestUrl.append(":").append(request.getServerPort());
      }
      return requestUrl.append(rewrittenPath);
    }
  }
}
