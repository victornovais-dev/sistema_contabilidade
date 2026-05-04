package com.sistema_contabilidade.monitoring.query;

import com.sistema_contabilidade.monitoring.RequestMonitoringPathUtils;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConfigurationProperties(prefix = "app.query-monitor")
@Slf4j
public class QueryCountFilter extends OncePerRequestFilter {

  public static final String QUERY_COUNT_HEADER = "X-Query-Count";
  static final String QUERY_COUNT_METRIC = "http.server.query.count";

  private boolean enabled = true;
  private int threshold = 15;
  private boolean addResponseHeader = true;
  private MeterRegistry meterRegistry;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !enabled || RequestMonitoringPathUtils.isIgnoredPath(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    QueryCountContext.start();
    try {
      filterChain.doFilter(request, response);
    } finally {
      int count = QueryCountContext.get();
      if (addResponseHeader) {
        response.setHeader(QUERY_COUNT_HEADER, Integer.toString(count));
      }
      recordMetric(request, response, count);
      logIfThresholdExceeded(request, count);
      QueryCountContext.clear();
    }
  }

  private void recordMetric(
      HttpServletRequest request, HttpServletResponse response, int queryCount) {
    if (meterRegistry == null) {
      return;
    }

    meterRegistry
        .summary(
            QUERY_COUNT_METRIC,
            "method",
            request.getMethod(),
            "uri",
            RequestMonitoringPathUtils.resolveRoute(request),
            "status",
            Integer.toString(response.getStatus()))
        .record(queryCount);
  }

  private void logIfThresholdExceeded(HttpServletRequest request, int count) {
    if (count <= threshold) {
      return;
    }

    if (log.isWarnEnabled()) {
      log.warn(
          "QUERY REVIEW NEEDED - {} {} executou {} consultas SQL (limite: {}). "
              + "Revise JOIN FETCH, @EntityGraph ou projecoes DTO.",
          request.getMethod(),
          request.getRequestURI(),
          count,
          threshold);
    }
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public void setThreshold(int threshold) {
    this.threshold = threshold;
  }

  public void setAddResponseHeader(boolean addResponseHeader) {
    this.addResponseHeader = addResponseHeader;
  }

  void setMeterRegistryForTest(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Autowired
  public void setMeterRegistry(ObjectProvider<MeterRegistry> meterRegistryProvider) {
    this.meterRegistry = meterRegistryProvider.getIfAvailable();
  }
}
