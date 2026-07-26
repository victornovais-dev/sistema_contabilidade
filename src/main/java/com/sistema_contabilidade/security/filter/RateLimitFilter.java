package com.sistema_contabilidade.security.filter;

import com.sistema_contabilidade.security.service.LocalRateLimitService;
import com.sistema_contabilidade.security.service.RateLimitDecision;
import com.sistema_contabilidade.security.service.ValkeyRateLimitService;
import com.sistema_contabilidade.security.util.RateLimitBucketResolver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

  static final String RATE_LIMIT_METRIC = "app.rate_limit.total";
  static final String TOO_MANY_REQUESTS_BODY =
      "{\"status\":429,\"message\":\"Limite de requisicoes excedido\"}";
  private static final String VALKEY_SOURCE = "valkey";

  private final ValkeyRateLimitService valkeyRateLimitService;
  private final LocalRateLimitService localRateLimitService;
  private final Counter valkeyAllowedCounter;
  private final Counter valkeyRejectedCounter;
  private final Counter valkeyErrorCounter;
  private final Counter localAllowedCounter;
  private final Counter localRejectedCounter;

  @Autowired
  public RateLimitFilter(
      ValkeyRateLimitService valkeyRateLimitService,
      LocalRateLimitService localRateLimitService,
      ObjectProvider<MeterRegistry> meterRegistryProvider) {
    this(
        valkeyRateLimitService,
        localRateLimitService,
        meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new));
  }

  RateLimitFilter(
      ValkeyRateLimitService valkeyRateLimitService,
      LocalRateLimitService localRateLimitService,
      MeterRegistry meterRegistry) {
    this.valkeyRateLimitService = valkeyRateLimitService;
    this.localRateLimitService = localRateLimitService;
    this.valkeyAllowedCounter = counter(meterRegistry, VALKEY_SOURCE, "allowed");
    this.valkeyRejectedCounter = counter(meterRegistry, VALKEY_SOURCE, "rejected");
    this.valkeyErrorCounter = counter(meterRegistry, VALKEY_SOURCE, "error");
    this.localAllowedCounter = counter(meterRegistry, "local", "allowed");
    this.localRejectedCounter = counter(meterRegistry, "local", "rejected");
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/v1/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String clientKey = RateLimitBucketResolver.resolve(request);
    RateLimitDecision decision = resolveDecision(clientKey);
    if (decision == RateLimitDecision.REJECTED) {
      writeTooManyRequests(response);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private RateLimitDecision resolveDecision(String clientKey) {
    if (valkeyRateLimitService.isEnabled()) {
      RateLimitDecision valkeyDecision = valkeyRateLimitService.tryAcquire(clientKey);
      if (valkeyDecision == RateLimitDecision.ALLOWED) {
        valkeyAllowedCounter.increment();
        return valkeyDecision;
      }
      if (valkeyDecision == RateLimitDecision.REJECTED) {
        valkeyRejectedCounter.increment();
        return valkeyDecision;
      }
      valkeyErrorCounter.increment();
    }
    RateLimitDecision localDecision = localRateLimitService.tryAcquire(clientKey);
    if (localDecision == RateLimitDecision.REJECTED) {
      localRejectedCounter.increment();
    } else {
      localAllowedCounter.increment();
    }
    return localDecision;
  }

  private void writeTooManyRequests(HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(TOO_MANY_REQUESTS_BODY);
  }

  private Counter counter(MeterRegistry meterRegistry, String backend, String result) {
    return Counter.builder(RATE_LIMIT_METRIC)
        .description("Decisoes do rate limit por backend")
        .tag("backend", backend)
        .tag("result", result)
        .register(meterRegistry);
  }
}
