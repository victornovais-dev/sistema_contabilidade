package com.sistema_contabilidade.security.config;

import com.sistema_contabilidade.security.filter.JwtAuthFilter;
import com.sistema_contabilidade.security.filter.RateLimitFilter;
import com.sistema_contabilidade.security.filter.RequestContextMdcFilter;
import com.sistema_contabilidade.security.util.SecurityPaths;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private static final String ADMIN_ROLE = "ADMIN";
  private static final String CONTABIL_ROLE = "CONTABIL";
  private static final String CONTABIL_AUTHORITY = "ROLE_CONTABIL";
  private static final String PATH_WILDCARD = "/**";
  private static final String[] PUBLIC_ACTUATOR_PATHS = {
    "/actuator/health", "/actuator/info", "/actuator/prometheus"
  };
  private static final String[] STATIC_RESOURCES = {
    "/assets/**", "/partials/**", "/src/**", "/css/**", "/js/**", "/images/**", "/webjars/**"
  };

  private final JwtAuthFilter jwtAuthFilter;
  private final RateLimitFilter rateLimitFilter;
  private final RequestContextMdcFilter requestContextMdcFilter;

  private int argon2SaltLength = 16;
  private int argon2HashLength = 32;
  private int argon2Parallelism = 2;
  private int argon2MemoryCost = 1 << 16;
  private int argon2TimeCost = 3;

  @Value("${app.security.argon2.salt-length:16}")
  void setArgon2SaltLength(int argon2SaltLength) {
    this.argon2SaltLength = argon2SaltLength;
  }

  @Value("${app.security.argon2.hash-length:32}")
  void setArgon2HashLength(int argon2HashLength) {
    this.argon2HashLength = argon2HashLength;
  }

  @Value("${app.security.argon2.parallelism:2}")
  void setArgon2Parallelism(int argon2Parallelism) {
    this.argon2Parallelism = argon2Parallelism;
  }

  @Value("${app.security.argon2.memory-cost:65536}")
  void setArgon2MemoryCost(int argon2MemoryCost) {
    this.argon2MemoryCost = argon2MemoryCost;
  }

  @Value("${app.security.argon2.time-cost:3}")
  void setArgon2TimeCost(int argon2TimeCost) {
    this.argon2TimeCost = argon2TimeCost;
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) {
    try {
      http.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository()))
          .cors(Customizer.withDefaults())
          .headers(this::configureHeaders)
          .sessionManagement(
              session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .exceptionHandling(
              exceptions ->
                  exceptions
                      .authenticationEntryPoint(authenticationEntryPoint())
                      .accessDeniedHandler(accessDeniedHandler()))
          .authorizeHttpRequests(this::configureAuthorization)
          .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
          .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
          .addFilterAfter(requestContextMdcFilter, JwtAuthFilter.class);
      return http.build();
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao construir SecurityFilterChain", exception);
    }
  }

  private void configureHeaders(
      org.springframework.security.config.annotation.web.configurers.HeadersConfigurer<HttpSecurity>
          headers) {
    headers.contentSecurityPolicy(
        csp ->
            csp.policyDirectives(
                "default-src 'self'; "
                    + "script-src 'self'; "
                    + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                    + "font-src 'self' https://fonts.gstatic.com data:; "
                    + "img-src 'self' data: https:; "
                    + "connect-src 'self'; "
                    + "object-src 'none'; "
                    + "frame-ancestors 'none'; "
                    + "base-uri 'self'; "
                    + "form-action 'self';"));
    headers.contentTypeOptions(Customizer.withDefaults());
    headers.frameOptions(
        org.springframework.security.config.annotation.web.configurers.HeadersConfigurer
                .FrameOptionsConfig
            ::deny);
    headers.referrerPolicy(
        referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER));
    headers.addHeaderWriter(
        new StaticHeadersWriter("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
    headers.addHeaderWriter(new StaticHeadersWriter("X-XSS-Protection", "0"));
    headers.httpStrictTransportSecurity(
        hsts -> hsts.maxAgeInSeconds(31536000).includeSubDomains(true));
  }

  private AuthenticationEntryPoint authenticationEntryPoint() {
    return (request, response, authException) -> {
      if (request.getRequestURI().startsWith(SecurityPaths.API_V1_PREFIX + "/")) {
        response.sendError(401);
        return;
      }
      response.sendRedirect(SecurityPaths.LOGIN_PAGE);
    };
  }

  private AccessDeniedHandler accessDeniedHandler() {
    return (request, response, accessDeniedException) -> {
      String requestUri = request.getRequestURI();
      if (isNotFoundProtectedPath(requestUri)) {
        response.sendRedirect(SecurityPaths.NOT_FOUND_PAGE);
        return;
      }
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
    };
  }

  private void configureAuthorization(
      org.springframework.security.config.annotation.web.configurers
                      .AuthorizeHttpRequestsConfigurer<
                  HttpSecurity>
              .AuthorizationManagerRequestMatcherRegistry
          auth) {
    auth.requestMatchers(
            SecurityPaths.ROOT_PATH,
            SecurityPaths.LOGIN_PAGE,
            SecurityPaths.NOT_FOUND_PAGE,
            SecurityPaths.ERROR_PAGE,
            SecurityPaths.ERROR_PAGE + PATH_WILDCARD,
            SecurityPaths.FAVICON_PATH)
        .permitAll()
        .requestMatchers(PUBLIC_ACTUATOR_PATHS)
        .permitAll()
        .requestMatchers(SecurityPaths.CREATE_USER_PAGE)
        .hasRole(ADMIN_ROLE)
        .requestMatchers(SecurityPaths.UPDATE_USER_PAGE)
        .hasRole(ADMIN_ROLE)
        .requestMatchers(SecurityPaths.ADD_RECEIPT_PAGE, SecurityPaths.ADD_RECEIPT_PAGE_HTML)
        .access(
            (authentication, context) ->
                new AuthorizationDecision(canAccessReceiptPage(authentication.get())))
        .requestMatchers(SecurityPaths.ADMIN_PAGE)
        .hasRole(ADMIN_ROLE)
        .requestMatchers(SecurityPaths.NOTIFICATIONS_PAGE, SecurityPaths.NOTIFICATIONS_PAGE_HTML)
        .hasAnyRole(ADMIN_ROLE, CONTABIL_ROLE)
        .requestMatchers(SecurityPaths.MANAGE_ROLES_PAGE)
        .hasRole(ADMIN_ROLE)
        .requestMatchers(STATIC_RESOURCES)
        .permitAll()
        .requestMatchers(SecurityPaths.AUTH_API_BASE + PATH_WILDCARD)
        .permitAll()
        .requestMatchers(SecurityPaths.USERS_ME_API_PATH)
        .authenticated()
        .requestMatchers(HttpMethod.PUT, SecurityPaths.USERS_ME_API_PATH)
        .authenticated()
        .requestMatchers(SecurityPaths.USERS_API_BASE + PATH_WILDCARD)
        .hasRole(ADMIN_ROLE)
        .requestMatchers(SecurityPaths.ADMIN_API_BASE + PATH_WILDCARD)
        .hasRole(ADMIN_ROLE)
        .requestMatchers(SecurityPaths.NOTIFICATIONS_API_PATTERN)
        .hasAnyRole(ADMIN_ROLE, CONTABIL_ROLE)
        .requestMatchers(SecurityPaths.RELATORIOS_ROLES_API_PATH)
        .authenticated()
        .anyRequest()
        .authenticated();
  }

  private boolean canAccessReceiptPage(
      org.springframework.security.core.Authentication authResult) {
    return authResult != null
        && authResult.isAuthenticated()
        && authResult.getAuthorities().stream()
            .noneMatch(authority -> CONTABIL_AUTHORITY.equals(authority.getAuthority()));
  }

  private boolean isNotFoundProtectedPath(String requestUri) {
    return List.of(
            SecurityPaths.ADMIN_PAGE,
            SecurityPaths.ADD_RECEIPT_PAGE,
            SecurityPaths.ADD_RECEIPT_PAGE_HTML,
            SecurityPaths.CREATE_USER_PAGE,
            SecurityPaths.CREATE_USER_PAGE_HTML,
            SecurityPaths.MANAGE_ROLES_PAGE,
            SecurityPaths.UPDATE_USER_PAGE,
            SecurityPaths.UPDATE_USER_PAGE_HTML)
        .contains(requestUri);
  }

  @Bean
  CookieCsrfTokenRepository csrfTokenRepository() {
    CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
    repository.setCookieCustomizer(builder -> builder.httpOnly(true));
    repository.setCookiePath("/");
    repository.setHeaderName("X-CSRF-TOKEN");
    return repository;
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${app.security.cors.allowed-origins:http://localhost:3000}") String allowedOrigins) {
    CorsConfiguration configuration = new CorsConfiguration();
    List<String> origins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList();
    configuration.setAllowedOrigins(origins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of(
            "Authorization",
            "Content-Type",
            "X-XSRF-TOKEN",
            "X-CSRF-TOKEN",
            "X-Request-ID",
            "X-Requested-With"));
    configuration.setExposedHeaders(
        List.of("X-Request-ID", "X-Query-Count", "X-App-Time-Ms", "Server-Timing"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    PasswordEncoder preferredEncoder =
        new Argon2PasswordEncoder(
            argon2SaltLength,
            argon2HashLength,
            argon2Parallelism,
            argon2MemoryCost,
            argon2TimeCost);
    return new LegacyCompatiblePasswordEncoder(
        PasswordEncoderFactories.createDelegatingPasswordEncoder(),
        preferredEncoder,
        SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8());
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) {
    try {
      return configuration.getAuthenticationManager();
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao obter AuthenticationManager", exception);
    }
  }
}
