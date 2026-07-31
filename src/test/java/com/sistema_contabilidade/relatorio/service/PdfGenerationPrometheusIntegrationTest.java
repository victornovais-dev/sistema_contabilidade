package com.sistema_contabilidade.relatorio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.monitoring.memory.service.MemoryRuntimeProbe;
import com.sistema_contabilidade.monitoring.memory.service.MemoryRuntimeSnapshot;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("PDF generation Prometheus integration tests")
class PdfGenerationPrometheusIntegrationTest {

  @LocalServerPort private int port;
  @Autowired private PdfGenerationLimiter limiter;
  @MockitoBean private MemoryRuntimeProbe memoryRuntimeProbe;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  @DisplayName("Actuator Prometheus deve expor capacidade, resultados, tempo e memoria do PDF")
  void actuatorPrometheusDeveExporMetricasDeGeracaoDePdf()
      throws IOException, InterruptedException {
    when(memoryRuntimeProbe.snapshot()).thenReturn(snapshot(100L, 1_000L), snapshot(160L, 1_250L));
    limiter.execute(() -> "pdf");

    HttpResponse<String> response = get("/actuator/prometheus");

    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
    assertThat(response.body())
        .contains("app_pdf_concurrent_active")
        .contains("app_pdf_concurrent_limit 2.0")
        .contains("app_pdf_queue_size")
        .contains("app_pdf_queue_capacity 4.0")
        .contains("app_pdf_requests_total")
        .contains("app_pdf_slot_held_seconds")
        .contains("app_pdf_memory_increase_bytes")
        .contains("result=\"success\"")
        .contains("scope=\"java_process\"")
        .contains("scope=\"container\"");
  }

  private MemoryRuntimeSnapshot snapshot(long processRssBytes, long containerUsageBytes) {
    return new MemoryRuntimeSnapshot(
        OptionalLong.of(processRssBytes),
        OptionalLong.of(containerUsageBytes),
        OptionalLong.of(2_000L));
  }

  private HttpResponse<String> get(String path) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
