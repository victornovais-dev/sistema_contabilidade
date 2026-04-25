package com.sistema_contabilidade.monitoring.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Query count Prometheus integration tests")
class QueryCountPrometheusIntegrationTest {

  @LocalServerPort private int port;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  @DisplayName("Actuator Prometheus deve expor query count por rota")
  void actuatorPrometheusDeveExporQueryCountPorRota() throws IOException, InterruptedException {
    get("/api/v1/itens");

    HttpResponse<String> response = get("/actuator/prometheus");

    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
    assertThat(response.body())
        .contains("http_server_query_count")
        .contains("uri=\"/api/v1/itens\"");
  }

  private HttpResponse<String> get(String path) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
