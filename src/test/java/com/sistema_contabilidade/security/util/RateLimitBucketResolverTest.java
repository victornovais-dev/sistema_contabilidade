package com.sistema_contabilidade.security.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("RateLimitBucketResolver unit tests")
class RateLimitBucketResolverTest {

  @Test
  @DisplayName("Deve normalizar IPv4 e IPv6 equivalentes")
  void deveNormalizarEnderecosEquivalentes() {
    assertThat(RateLimitBucketResolver.normalizeIp("010.000.000.001")).isEqualTo("10.0.0.1");
    assertThat(RateLimitBucketResolver.normalizeIp("::1"))
        .isEqualTo(RateLimitBucketResolver.normalizeIp("0:0:0:0:0:0:0:1"));
  }

  @Test
  @DisplayName("Deve normalizar barras sufixos e identificadores da URI")
  void deveNormalizarUri() {
    String first =
        RateLimitBucketResolver.normalizeUri(
            "/api//v1/itens/00000000-0000-0000-0000-000000000001/arquivos/123/?pagina=2");
    String second =
        RateLimitBucketResolver.normalizeUri(
            "/api/v1/itens/10000000-0000-0000-0000-000000000002/arquivos/999");

    assertThat(first).isEqualTo("/api/v1/itens/{id}/arquivos/{id}");
    assertThat(second).isEqualTo(first);
  }

  @Test
  @DisplayName("Bucket deve conter somente SHA-256 e ignorar X-Forwarded-For bruto")
  void bucketDeveUsarRemoteAddressEEsconderDadosBrutos() {
    MockHttpServletRequest first = request("10.0.0.1", "203.0.113.10");
    MockHttpServletRequest sameRemoteAddress = request("10.0.0.1", "198.51.100.20");
    MockHttpServletRequest differentRemoteAddress = request("10.0.0.2", "203.0.113.10");

    String firstBucket = RateLimitBucketResolver.resolve(first);

    assertThat(firstBucket).matches("[0-9a-f]{64}").doesNotContain("10.0.0.1", "/api/v1/usuarios");
    assertThat(RateLimitBucketResolver.resolve(sameRemoteAddress)).isEqualTo(firstBucket);
    assertThat(RateLimitBucketResolver.resolve(differentRemoteAddress)).isNotEqualTo(firstBucket);
  }

  @Test
  @DisplayName("Metodos diferentes devem usar buckets diferentes")
  void metodosDiferentesDevemUsarBucketsDiferentes() {
    MockHttpServletRequest get = request("10.0.0.1", null);
    MockHttpServletRequest post = new MockHttpServletRequest("POST", "/api/v1/usuarios");
    post.setRemoteAddr("10.0.0.1");

    assertThat(RateLimitBucketResolver.resolve(get))
        .isNotEqualTo(RateLimitBucketResolver.resolve(post));
  }

  private MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    request.setRemoteAddr(remoteAddress);
    if (forwardedFor != null) {
      request.addHeader("X-Forwarded-For", forwardedFor);
    }
    return request;
  }
}
