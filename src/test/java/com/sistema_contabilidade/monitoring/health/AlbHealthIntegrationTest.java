package com.sistema_contabilidade.monitoring.health;

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
@DisplayName("ALB health integration tests")
class AlbHealthIntegrationTest {

  @LocalServerPort private int port;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  @DisplayName("Health do ALB deve expor somente ping e writer sem autenticacao")
  void healthDoAlbDeveExporSomentePingEWriterSemAutenticacao()
      throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health/alb"))
            .GET()
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
    assertThat(response.body())
        .contains("\"status\":\"UP\"")
        .contains("\"ping\"")
        .contains("\"writer\"")
        .doesNotContain("\"db\"", "\"redis\"", "\"reader\"");
  }
}
