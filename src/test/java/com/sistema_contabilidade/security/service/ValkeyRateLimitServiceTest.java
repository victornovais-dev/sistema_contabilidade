package com.sistema_contabilidade.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValkeyRateLimitService unit tests")
class ValkeyRateLimitServiceTest {

  @Mock private StringRedisTemplate redisTemplate;

  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
  }

  @Test
  @DisplayName("Script deve implementar janela deslizante atomica completa")
  void scriptDeveImplementarJanelaDeslizanteAtomicaCompleta() {
    assertThat(ValkeyRateLimitService.SCRIPT_TEXT)
        .contains("redis.call('TIME')", "ZREMRANGEBYSCORE", "ZCARD", "ZADD", "PEXPIRE");
  }

  @Test
  @DisplayName("Deve aceitar e rejeitar conforme retorno atomico")
  void deveAceitarERejeitarConformeRetornoAtomico() {
    ValkeyRateLimitService service = service(true, 2, 60);
    when(executeScript()).thenReturn(1L, 0L);

    assertThat(service.tryAcquire("abc123")).isEqualTo(RateLimitDecision.ALLOWED);
    assertThat(service.tryAcquire("abc123")).isEqualTo(RateLimitDecision.REJECTED);
  }

  @Test
  @DisplayName("Chave deve usar somente prefixo e hash recebido")
  void chaveDeveUsarSomentePrefixoEHashRecebido() {
    ValkeyRateLimitService service = service(true, 120, 60);
    when(executeScript()).thenReturn(1L);

    service.tryAcquire("0123456789abcdef");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
    verify(redisTemplate)
        .execute(any(RedisScript.class), keysCaptor.capture(), eq("60000"), eq("120"), anyString());
    assertThat(keysCaptor.getValue())
        .containsExactly(ValkeyRateLimitService.KEY_PREFIX + "0123456789abcdef");
  }

  @Test
  @DisplayName("Falha deve retornar indisponivel e recuperar na proxima chamada")
  void falhaDeveRetornarIndisponivelERecuperar() {
    ValkeyRateLimitService service = service(true, 120, 60);
    when(executeScript()).thenThrow(new IllegalStateException("offline")).thenReturn(1L);

    assertThat(service.tryAcquire("bucket")).isEqualTo(RateLimitDecision.UNAVAILABLE);
    assertThat(service.tryAcquire("bucket")).isEqualTo(RateLimitDecision.ALLOWED);
    assertErrorMetric(1.0);
  }

  @Test
  @DisplayName("Resposta nula deve acionar fallback")
  void respostaNulaDeveAcionarFallback() {
    ValkeyRateLimitService service = service(true, 120, 60);
    when(executeScript()).thenReturn(null);

    assertThat(service.tryAcquire("bucket")).isEqualTo(RateLimitDecision.UNAVAILABLE);
    assertErrorMetric(1.0);
  }

  @Test
  @DisplayName("Backend desabilitado nao deve acessar Redis")
  void backendDesabilitadoNaoDeveAcessarRedis() {
    ValkeyRateLimitService service = service(false, 120, 60);

    assertThat(service.tryAcquire("bucket")).isEqualTo(RateLimitDecision.UNAVAILABLE);
    verifyNoInteractions(redisTemplate);
    assertErrorMetric(0.0);
  }

  @Test
  @DisplayName("Configuracao invalida deve falhar no startup")
  void configuracaoInvalidaDeveFalharNoStartup() {
    assertThatThrownBy(() -> service(true, 0, 60)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service(true, 1, 0)).isInstanceOf(IllegalArgumentException.class);
    verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(), any(), any());
  }

  @SuppressWarnings("unchecked")
  private Long executeScript() {
    RedisScript<Long> script = any(RedisScript.class);
    return redisTemplate.execute(script, anyList(), anyString(), anyString(), anyString());
  }

  private ValkeyRateLimitService service(boolean enabled, int maxRequests, long windowSeconds) {
    return new ValkeyRateLimitService(
        redisTemplate, meterRegistry, maxRequests, windowSeconds, enabled);
  }

  private void assertErrorMetric(double expected) {
    Counter counter =
        meterRegistry
            .find(ValkeyRateLimitService.VALKEY_ERROR_METRIC)
            .tag("operation", "rate_limit")
            .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(expected);
  }
}
