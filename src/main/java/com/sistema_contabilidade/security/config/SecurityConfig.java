package com.sistema_contabilidade.security.config;

import com.sistema_contabilidade.security.filter.JwtAuthFilter;
import com.sistema_contabilidade.security.filter.RateLimitFilter;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthFilter jwtAuthFilter;
  private final RateLimitFilter rateLimitFilter;

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) {
    try {
      http.csrf(AbstractHttpConfigurer::disable)
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
          .authorizeHttpRequests(
              auth ->
                  auth.requestMatchers(
                          "/",
                          "/index.html",
                          "/login.html",
                          "/home.html",
                          "/adicionar_comprovante.html",
                          "/lista_comprovantes.html",
                          "/relatorios.html",
                          "/relatorio_pdf.html",
                          "/create-user.html",
                          "/favicon.ico")
                      .permitAll()
                      .requestMatchers("/criar_usuario")
                      .hasRole("ADMIN")
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
                      .requestMatchers("/api/v1/admin/**")
                      .hasRole("ADMIN")
                      .anyRequest()
                      .authenticated())
          .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
          .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
      return http.build();
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao construir SecurityFilterChain", exception);
    }
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${app.security.cors.allowed-origins:http://localhost:3000}") String allowedOrigins) {
    CorsConfiguration configuration = new CorsConfiguration();
    List<String> origins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList();
    configuration.setAllowedOrigins(origins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "X-XSRF-TOKEN", "X-CSRF-TOKEN"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8();
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
