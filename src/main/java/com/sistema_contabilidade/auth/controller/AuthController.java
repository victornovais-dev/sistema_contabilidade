package com.sistema_contabilidade.auth.controller;

import com.sistema_contabilidade.auth.dto.AuthenticatedLoginResult;
import com.sistema_contabilidade.auth.dto.CompleteNewPasswordRequest;
import com.sistema_contabilidade.auth.dto.JwtLoginResponse;
import com.sistema_contabilidade.auth.dto.LoginApiResponse;
import com.sistema_contabilidade.auth.dto.LoginRequest;
import com.sistema_contabilidade.auth.service.AuthLoginChallenge;
import com.sistema_contabilidade.auth.service.AuthService;
import com.sistema_contabilidade.auth.service.LoginChallengeCookieService;
import com.sistema_contabilidade.auth.service.LoginFlowResult;
import com.sistema_contabilidade.security.service.AdminRouteService;
import com.sistema_contabilidade.security.util.SecurityPaths;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(SecurityPaths.AUTH_API_BASE)
@Validated
@RequiredArgsConstructor
public class AuthController {

  private static final String LEGACY_AUTH_COOKIE_NAME = "SC_TOKEN";
  private static final String CHALLENGE_COOKIE_NAME = "SC_LOGIN_CHALLENGE";
  private static final String COOKIE_SAME_SITE = "Strict";

  private final AuthService authService;
  private final AdminRouteService adminRouteService;
  private final LoginChallengeCookieService loginChallengeCookieService;

  @Value("${app.session.cookie-name:SC_SESSION}")
  private String sessionCookieName = "SC_SESSION";

  @Value("${app.session.ttl-minutes:480}")
  private long sessionTtlMinutes = 480L;

  @Value("${app.session.challenge-ttl-minutes:10}")
  private long challengeTtlMinutes = 10L;

  @PostMapping("/login")
  public ResponseEntity<LoginApiResponse> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    LoginFlowResult loginResult = authService.login(request, httpRequest);
    if (loginResult.challengeRequired()) {
      httpResponse.addHeader(
          HttpHeaders.SET_COOKIE,
          buildChallengeCookie(
                  loginChallengeCookieService.createToken(loginResult.challenge()), httpRequest)
              .toString());
      httpResponse.addHeader(
          HttpHeaders.SET_COOKIE, clearSessionCookie(httpRequestIsSecure(httpRequest)).toString());
      httpResponse.addHeader(
          HttpHeaders.SET_COOKIE,
          clearLegacyAuthCookie(httpRequestIsSecure(httpRequest)).toString());
      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .body(
              LoginApiResponse.challenge(
                  loginResult.challenge().challengeName(), loginResult.challenge().message()));
    }
    AuthenticatedLoginResult authenticatedResult = loginResult.authenticatedResult();
    httpResponse.addHeader(
        HttpHeaders.SET_COOKIE,
        buildSessionCookie(authenticatedResult.sessionToken(), httpRequest).toString());
    httpResponse.addHeader(
        HttpHeaders.SET_COOKIE, clearLegacyAuthCookie(httpRequestIsSecure(httpRequest)).toString());
    httpResponse.addHeader(
        HttpHeaders.SET_COOKIE, clearChallengeCookie(httpRequestIsSecure(httpRequest)).toString());
    return ResponseEntity.ok(LoginApiResponse.authenticated(authenticatedResult.response()));
  }

  @PostMapping("/complete-new-password")
  public ResponseEntity<LoginApiResponse> completeNewPassword(
      @Valid @RequestBody CompleteNewPasswordRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    AuthLoginChallenge challenge =
        loginChallengeCookieService.parseToken(resolveChallengeToken(httpRequest));
    AuthenticatedLoginResult loginResult =
        authService.completeNewPassword(challenge, request, httpRequest);
    httpResponse.addHeader(
        HttpHeaders.SET_COOKIE,
        buildSessionCookie(loginResult.sessionToken(), httpRequest).toString());
    httpResponse.addHeader(
        HttpHeaders.SET_COOKIE, clearLegacyAuthCookie(httpRequestIsSecure(httpRequest)).toString());
    httpResponse.addHeader(
        HttpHeaders.SET_COOKIE, clearChallengeCookie(httpRequestIsSecure(httpRequest)).toString());
    return ResponseEntity.ok(LoginApiResponse.authenticated(loginResult.response()));
  }

  @PostMapping("/refresh")
  public ResponseEntity<JwtLoginResponse> refresh(HttpServletRequest request) {
    return ResponseEntity.ok(authService.refresh(resolveSessionToken(request), request));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse httpResponse) {
    authService.logout(resolveSessionToken(request));
    httpResponse.addHeader(
        HttpHeaders.SET_COOKIE, clearSessionCookie(httpRequestIsSecure(request)).toString());
    httpResponse.addHeader(
        HttpHeaders.SET_COOKIE, clearLegacyAuthCookie(httpRequestIsSecure(request)).toString());
    httpResponse.addHeader(
        HttpHeaders.SET_COOKIE, clearChallengeCookie(httpRequestIsSecure(request)).toString());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public ResponseEntity<String> me(Authentication authentication) {
    return ResponseEntity.ok(authentication.getName());
  }

  @GetMapping("/me/roles")
  public ResponseEntity<List<String>> meRoles(Authentication authentication) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(List.of());
    }
    List<String> roles =
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(Objects::nonNull)
            .filter(authority -> authority.startsWith("ROLE_"))
            .map(authority -> authority.substring("ROLE_".length()))
            .distinct()
            .sorted()
            .toList();
    return ResponseEntity.ok(roles);
  }

  @GetMapping("/csrf")
  public ResponseEntity<Map<String, String>> csrf(CsrfToken csrfToken) {
    return ResponseEntity.ok(Map.of("token", csrfToken.getToken()));
  }

  @GetMapping("/routes")
  public ResponseEntity<Map<String, String>> routes(Authentication authentication) {
    return ResponseEntity.ok(adminRouteService.routeConfig());
  }

  private String resolveSessionToken(HttpServletRequest request) {
    return resolveCookieValue(request, sessionCookieName);
  }

  private String resolveChallengeToken(HttpServletRequest request) {
    return resolveCookieValue(request, CHALLENGE_COOKIE_NAME);
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

  private ResponseCookie buildSessionCookie(String token, HttpServletRequest request) {
    return ResponseCookie.from(sessionCookieName, token)
        .httpOnly(true)
        .secure(httpRequestIsSecure(request))
        .sameSite(COOKIE_SAME_SITE)
        .path(SecurityPaths.ROOT_PATH)
        .maxAge(Duration.ofMinutes(sessionTtlMinutes))
        .build();
  }

  private ResponseCookie buildChallengeCookie(String token, HttpServletRequest request) {
    return ResponseCookie.from(CHALLENGE_COOKIE_NAME, token)
        .httpOnly(true)
        .secure(httpRequestIsSecure(request))
        .sameSite(COOKIE_SAME_SITE)
        .path(SecurityPaths.AUTH_API_BASE)
        .maxAge(Duration.ofMinutes(challengeTtlMinutes))
        .build();
  }

  private ResponseCookie clearSessionCookie(boolean secure) {
    return ResponseCookie.from(sessionCookieName, "")
        .httpOnly(true)
        .secure(secure)
        .sameSite(COOKIE_SAME_SITE)
        .path(SecurityPaths.ROOT_PATH)
        .maxAge(0)
        .build();
  }

  private ResponseCookie clearChallengeCookie(boolean secure) {
    return ResponseCookie.from(CHALLENGE_COOKIE_NAME, "")
        .httpOnly(true)
        .secure(secure)
        .sameSite(COOKIE_SAME_SITE)
        .path(SecurityPaths.AUTH_API_BASE)
        .maxAge(0)
        .build();
  }

  private ResponseCookie clearLegacyAuthCookie(boolean secure) {
    return ResponseCookie.from(LEGACY_AUTH_COOKIE_NAME, "")
        .httpOnly(true)
        .secure(secure)
        .sameSite(COOKIE_SAME_SITE)
        .path(SecurityPaths.ROOT_PATH)
        .maxAge(0)
        .build();
  }

  private boolean httpRequestIsSecure(HttpServletRequest request) {
    String forwardedProto = request.getHeader("X-Forwarded-Proto");
    return request.isSecure() || "https".equalsIgnoreCase(forwardedProto);
  }
}
