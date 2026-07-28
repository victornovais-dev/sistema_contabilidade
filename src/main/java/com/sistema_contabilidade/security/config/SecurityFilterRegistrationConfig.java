package com.sistema_contabilidade.security.config;

import com.sistema_contabilidade.security.filter.DatabaseRoutingFilter;
import com.sistema_contabilidade.security.filter.JwtAuthFilter;
import com.sistema_contabilidade.security.filter.RateLimitFilter;
import com.sistema_contabilidade.security.filter.RequestContextMdcFilter;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityFilterRegistrationConfig {

  @Bean
  FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
    return disabledRegistration(filter);
  }

  @Bean
  FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
    return disabledRegistration(filter);
  }

  @Bean
  FilterRegistrationBean<DatabaseRoutingFilter> databaseRoutingFilterRegistration(
      DatabaseRoutingFilter filter) {
    return disabledRegistration(filter);
  }

  @Bean
  FilterRegistrationBean<RequestContextMdcFilter> requestContextMdcFilterRegistration(
      RequestContextMdcFilter filter) {
    return disabledRegistration(filter);
  }

  private <T extends Filter> FilterRegistrationBean<T> disabledRegistration(T filter) {
    FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
