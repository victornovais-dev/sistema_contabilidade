package com.sistema_contabilidade.monitoring.http;

import com.sistema_contabilidade.monitoring.RequestMonitoringPathUtils;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConfigurationProperties(prefix = "app.request-timing")
@Slf4j
public class RequestTimingFilter extends OncePerRequestFilter {

  public static final String APP_TIME_HEADER = "X-App-Time-Ms";
  public static final String SERVER_TIMING_HEADER = "Server-Timing";
  static final String REQUEST_DURATION_METRIC = "http.server.app.duration";

  private boolean enabled = true;
  private boolean addResponseHeaders = true;
  private long logThresholdMs = 750L;
  private MeterRegistry meterRegistry;

  @Override
  protected boolean shouldNotFilter(jakarta.servlet.http.HttpServletRequest request) {
    return !enabled || RequestMonitoringPathUtils.isIgnoredPath(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      jakarta.servlet.http.HttpServletRequest request,
      jakarta.servlet.http.HttpServletResponse response,
      jakarta.servlet.FilterChain filterChain)
      throws jakarta.servlet.ServletException, IOException {
    long startedAt = System.nanoTime();
    ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
    try {
      filterChain.doFilter(request, responseWrapper);
    } finally {
      long durationNanos = System.nanoTime() - startedAt;
      long durationMs = Duration.ofNanos(durationNanos).toMillis();

      writeTimingHeaders(responseWrapper, durationMs);

      recordMetric(request, responseWrapper, durationNanos);
      logIfSlow(request, durationMs, responseWrapper.getStatus());
      responseWrapper.copyBodyToResponse();
    }
  }

  private void recordMetric(
      jakarta.servlet.http.HttpServletRequest request,
      jakarta.servlet.http.HttpServletResponse response,
      long durationNanos) {
    if (meterRegistry == null) {
      return;
    }

    meterRegistry
        .timer(
            REQUEST_DURATION_METRIC,
            "method",
            request.getMethod(),
            "uri",
            RequestMonitoringPathUtils.resolveRoute(request),
            "status",
            Integer.toString(response.getStatus()))
        .record(Duration.ofNanos(durationNanos));
  }

  private void writeTimingHeaders(
      jakarta.servlet.http.HttpServletResponse response, long durationMs) {
    if (!addResponseHeaders) {
      return;
    }
    response.setHeader(APP_TIME_HEADER, Long.toString(durationMs));
    response.setHeader(SERVER_TIMING_HEADER, "app;dur=" + durationMs);
  }

  private void logIfSlow(
      jakarta.servlet.http.HttpServletRequest request, long durationMs, int status) {
    if (durationMs < logThresholdMs) {
      return;
    }
    if (log.isWarnEnabled()) {
      log.warn(
          "REQUEST SLOW - {} {} respondeu {} em {}ms",
          request.getMethod(),
          request.getRequestURI(),
          status,
          durationMs);
    }
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public void setAddResponseHeaders(boolean addResponseHeaders) {
    this.addResponseHeaders = addResponseHeaders;
  }

  public void setLogThresholdMs(long logThresholdMs) {
    this.logThresholdMs = logThresholdMs;
  }

  void setMeterRegistryForTest(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Autowired
  public void setMeterRegistry(ObjectProvider<MeterRegistry> meterRegistryProvider) {
    this.meterRegistry = meterRegistryProvider.getIfAvailable();
  }
}
