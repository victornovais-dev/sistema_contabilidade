package com.sistema_contabilidade.security.config;

import com.sistema_contabilidade.security.filter.JwtAuthFilter;
import com.sistema_contabilidade.security.filter.RateLimitFilter;
import com.sistema_contabilidade.security.filter.RequestContextMdcFilter;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
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
  private static final String ADMIN_PATH = "/admin";
  private static final String MANAGE_ROLES_PATH = "/gerenciar_roles";

  private final JwtAuthFilter jwtAuthFilter;
  private final RateLimitFilter rateLimitFilter;
  private final RequestContextMdcFilter requestContextMdcFilter;

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) {
    try {
      http.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository()))
          .cors(Customizer.withDefaults())
          .headers(
              headers -> {
                headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self';"));
                headers.frameOptions(
                    org.springframework.security.config.annotation.web.configurers.HeadersConfigurer
                            .FrameOptionsConfig
                        ::deny);
                headers.referrerPolicy(
                    referrer ->
                        referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER));
                headers.addHeaderWriter(
                    new StaticHeadersWriter(
                        "Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
                headers.httpStrictTransportSecurity(
                    hsts -> hsts.maxAgeInSeconds(31536000).includeSubDomains(true));
              })
          .sessionManagement(
              session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .exceptionHandling(
              exceptions ->
                  exceptions
                      .authenticationEntryPoint(
                          (request, response, authException) -> {
                            String requestUri = request.getRequestURI();
                            if (requestUri.startsWith("/api/")) {
                              response.sendError(401);
                              return;
                            }
                            response.sendRedirect("/login");
                          })
                      .accessDeniedHandler(
                          (request, response, accessDeniedException) -> {
                            String requestUri = request.getRequestURI();
                            if (ADMIN_PATH.equals(requestUri)
                                || MANAGE_ROLES_PATH.equals(requestUri)) {
                              response.sendRedirect("/404");
                              return;
                            }
                            if (requestUri.startsWith("/api/")) {
                              response.sendError(403);
                              return;
                            }
                            response.sendError(403);
                          }))
          .authorizeHttpRequests(
              auth ->
                  auth.requestMatchers("/", "/login", "/404", "/error", "/error/**", "/favicon.ico")
                      .permitAll()
                      .requestMatchers("/criar_usuario")
                      .hasRole(ADMIN_ROLE)
                      .requestMatchers("/atualizar_usuario")
                      .hasRole(ADMIN_ROLE)
                      .requestMatchers(ADMIN_PATH)
                      .hasRole(ADMIN_ROLE)
                      .requestMatchers(MANAGE_ROLES_PATH)
                      .hasRole(ADMIN_ROLE)
                      .requestMatchers(
                          "/assets/**",
                          "/partials/**",
                          "/src/**",
                          "/css/**",
                          "/js/**",
                          "/images/**",
                          "/webjars/**")
                      .permitAll()
                      .requestMatchers("/api/v1/auth/**")
                      .permitAll()
                      .requestMatchers("/api/v1/usuarios/me")
                      .authenticated()
                      .requestMatchers(HttpMethod.PUT, "/api/v1/usuarios/me")
                      .authenticated()
                      .requestMatchers("/api/v1/usuarios/**")
                      .hasRole(ADMIN_ROLE)
                      .requestMatchers("/api/v1/admin/**")
                      .hasRole(ADMIN_ROLE)
                      .requestMatchers("/api/v1/relatorios/roles")
                      .authenticated()
                      .anyRequest()
                      .authenticated())
          .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
          .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
          .addFilterAfter(requestContextMdcFilter, JwtAuthFilter.class);
      return http.build();
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao construir SecurityFilterChain", exception);
    }
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
        List.of("Authorization", "Content-Type", "X-XSRF-TOKEN", "X-CSRF-TOKEN", "X-Request-ID"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    PasswordEncoder preferredEncoder = SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8();
    return new LegacyCompatiblePasswordEncoder(
        PasswordEncoderFactories.createDelegatingPasswordEncoder(), preferredEncoder);
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
