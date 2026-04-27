package com.sistema_contabilidade.security.filter;

import com.sistema_contabilidade.auth.service.SessaoUsuarioService;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import com.sistema_contabilidade.security.service.RequestFingerprintService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

  private static final String LEGACY_AUTH_COOKIE_NAME = "SC_TOKEN";

  private final JwtService jwtService;
  private final CustomUserDetailsService userDetailsService;
  private final SessaoUsuarioService sessaoUsuarioService;
  private final RequestFingerprintService requestFingerprintService;

  @Value("${app.session.cookie-name:SC_SESSION}")
  private String sessionCookieName = "SC_SESSION";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      authenticateComJwt(request, response);
    }
    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      authenticateComSessao(request, response);
    }
    filterChain.doFilter(request, response);
  }

  private void authenticateComJwt(HttpServletRequest request, HttpServletResponse response) {
    String headerToken = resolveBearerToken(request);
    if (headerToken != null && !headerToken.isBlank()) {
      tryAuthenticateJwtToken(headerToken, request);
      return;
    }

    String cookieToken = resolveCookieValue(request, LEGACY_AUTH_COOKIE_NAME);
    if (cookieToken == null || cookieToken.isBlank()) {
      return;
    }
    if (!tryAuthenticateJwtToken(cookieToken, request)) {
      clearLegacyAuthCookie(response, isSecureRequest(request));
    }
  }

  private boolean tryAuthenticateJwtToken(String token, HttpServletRequest request) {
    try {
      String username = jwtService.extractUsername(token);
      if (username == null) {
        return false;
      }
      UserDetails userDetails = userDetailsService.loadUserByUsername(username);
      String currentFingerprint = requestFingerprintService.generateFingerprint(request);
      if (!jwtService.isTokenValid(token, userDetails, currentFingerprint)) {
        return false;
      }
      authenticateRequest(request, userDetails);
      return true;
    } catch (JwtException | IllegalArgumentException | ResponseStatusException exception) {
      log.debug("Token invalido ou expirado detectado no filtro JWT", exception);
      SecurityContextHolder.clearContext();
      return false;
    }
  }

  private void authenticateComSessao(HttpServletRequest request, HttpServletResponse response) {
    String sessionToken = resolveCookieValue(request, sessionCookieName);
    if (sessionToken == null || sessionToken.isBlank()) {
      return;
    }
    try {
      UUID usuarioId = sessaoUsuarioService.validarSessao(sessionToken);
      UserDetails userDetails = userDetailsService.loadUserById(usuarioId);
      authenticateRequest(request, userDetails);
    } catch (ResponseStatusException exception) {
      if (log.isDebugEnabled()) {
        log.debug("Sessao invalida detectada no filtro de autenticacao", exception);
      }
      SecurityContextHolder.clearContext();
      clearSessionCookie(response, isSecureRequest(request));
    }
  }

  private void authenticateRequest(HttpServletRequest request, UserDetails userDetails) {
    UsernamePasswordAuthenticationToken authToken =
        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authToken);
  }

  private void clearLegacyAuthCookie(HttpServletResponse response, boolean secure) {
    Cookie cookie = new Cookie(LEGACY_AUTH_COOKIE_NAME, "");
    cookie.setHttpOnly(true);
    cookie.setSecure(secure);
    cookie.setPath("/");
    cookie.setMaxAge(0);
    response.addCookie(cookie);
  }

  private void clearSessionCookie(HttpServletResponse response, boolean secure) {
    Cookie cookie = new Cookie(sessionCookieName, "");
    cookie.setHttpOnly(true);
    cookie.setSecure(secure);
    cookie.setPath("/");
    cookie.setMaxAge(0);
    response.addCookie(cookie);
  }

  private String resolveBearerToken(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      return authHeader.substring(7);
    }
    return null;
  }

  private String resolveCookieValue(HttpServletRequest request, String cookieName) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
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
