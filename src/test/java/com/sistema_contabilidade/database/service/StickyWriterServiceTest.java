package com.sistema_contabilidade.database.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("StickyWriterService unit tests")
class StickyWriterServiceTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  @Test
  @DisplayName("Deve consultar chave de sessao sem cookie token ou PII")
  void deveConsultarChaveDeSessaoSemCookieTokenOuPii() {
    UUID sessionId = UUID.randomUUID();
    String key = StickyWriterService.KEY_PREFIX + sessionId;
    StickyWriterService service = service(true, 10);
    when(redisTemplate.hasKey(key)).thenReturn(true);

    assertTrue(service.requiresWriter(sessionId));

    verify(redisTemplate).hasKey(key);
  }

  @Test
  @DisplayName("Deve permitir reader depois da expiracao da chave")
  void devePermitirReaderDepoisDaExpiracaoDaChave() {
    UUID sessionId = UUID.randomUUID();
    String key = StickyWriterService.KEY_PREFIX + sessionId;
    StickyWriterService service = service(true, 10);
    when(redisTemplate.hasKey(key)).thenReturn(true, false);

    assertTrue(service.requiresWriter(sessionId));
    assertFalse(service.requiresWriter(sessionId));
  }

  @Test
  @DisplayName("Marcador assinado mantem writer apos troca de instancia e falha de renovacao")
  void marcadorAssinadoMantemWriterAposTrocaDeInstanciaEFalhaDeRenovacao() {
    UUID sessionId = UUID.randomUUID();
    AtomicLong epochSeconds = new AtomicLong(1_000L);
    StickyWriterService firstInstance = service(true, 10, epochSeconds);
    when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("Valkey indisponivel"));
    assertDoesNotThrow(() -> firstInstance.markWriter(sessionId));
    String marker = firstInstance.createSignedMarker(sessionId);
    StickyWriterService secondInstance = service(true, 10, epochSeconds);

    when(redisTemplate.hasKey(StickyWriterService.KEY_PREFIX + sessionId)).thenReturn(false);

    assertTrue(secondInstance.requiresWriter(sessionId, marker));
    assertFalse(marker.contains(sessionId.toString()));
    assertMetric(serviceRegistry, "marker_active", 1.0);
  }

  @Test
  @DisplayName("Marcador alterado deve forcar writer")
  void marcadorAlteradoDeveForcarWriter() {
    UUID sessionId = UUID.randomUUID();
    StickyWriterService service = service(true, 10);
    when(redisTemplate.hasKey(StickyWriterService.KEY_PREFIX + sessionId)).thenReturn(false);

    assertTrue(service.requiresWriter(sessionId, "v1.9999999999.assinatura-alterada"));
    assertMetric(serviceRegistry, "marker_invalid", 1.0);
  }

  @Test
  @DisplayName("Marcador expirado permite reader")
  void marcadorExpiradoPermiteReader() {
    UUID sessionId = UUID.randomUUID();
    AtomicLong epochSeconds = new AtomicLong(1_000L);
    StickyWriterService service = service(true, 10, epochSeconds);
    String marker = service.createSignedMarker(sessionId);
    epochSeconds.set(1_010L);
    when(redisTemplate.hasKey(StickyWriterService.KEY_PREFIX + sessionId)).thenReturn(false);

    assertFalse(service.requiresWriter(sessionId, marker));
    assertMetric(serviceRegistry, "marker_expired", 1.0);
  }

  @Test
  @DisplayName("Deve renovar sticky com TTL configurado")
  void deveRenovarStickyComTtlConfigurado() {
    UUID sessionId = UUID.randomUUID();
    StickyWriterService service = service(true, 17);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    service.markWriter(sessionId);

    verify(valueOperations)
        .set(StickyWriterService.KEY_PREFIX + sessionId, "1", Duration.ofSeconds(17));
  }

  @Test
  @DisplayName("Nova gravacao deve renovar mesma chave")
  void novaGravacaoDeveRenovarMesmaChave() {
    UUID sessionId = UUID.randomUUID();
    StickyWriterService service = service(true, 10);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    service.markWriter(sessionId);
    service.markWriter(sessionId);

    verify(valueOperations, org.mockito.Mockito.times(2))
        .set(StickyWriterService.KEY_PREFIX + sessionId, "1", Duration.ofSeconds(10));
  }

  @Test
  @DisplayName("Erro Valkey na consulta deve forcar writer")
  void erroValkeyNaConsultaDeveForcarWriter() {
    StickyWriterService service = service(true, 10);
    when(redisTemplate.hasKey(org.mockito.ArgumentMatchers.anyString()))
        .thenThrow(new IllegalStateException("Valkey indisponivel"));

    assertTrue(service.requiresWriter(UUID.randomUUID()));
    assertMetric(serviceRegistry, "read_error", 1.0);
  }

  @Test
  @DisplayName("Erro Valkey ao marcar nao deve falhar mutacao")
  void erroValkeyAoMarcarNaoDeveFalharMutacao() {
    StickyWriterService service = service(true, 10);
    when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("Valkey indisponivel"));

    assertDoesNotThrow(() -> service.markWriter(UUID.randomUUID()));
    assertMetric(serviceRegistry, "write_error", 1.0);
  }

  @Test
  @DisplayName("Sessoes devem usar chaves isoladas")
  void sessoesDevemUsarChavesIsoladas() {
    UUID firstSession = UUID.randomUUID();
    UUID secondSession = UUID.randomUUID();
    StickyWriterService service = service(true, 10);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    service.markWriter(firstSession);
    service.markWriter(secondSession);

    verify(valueOperations)
        .set(StickyWriterService.KEY_PREFIX + firstSession, "1", Duration.ofSeconds(10));
    verify(valueOperations)
        .set(StickyWriterService.KEY_PREFIX + secondSession, "1", Duration.ofSeconds(10));
  }

  @Test
  @DisplayName("Routing desabilitado nao deve acessar Valkey")
  void routingDesabilitadoNaoDeveAcessarValkey() {
    StickyWriterService service = service(false, 10);

    assertFalse(service.requiresWriter(UUID.randomUUID()));
    service.markWriter(UUID.randomUUID());

    verifyNoInteractions(redisTemplate);
  }

  @Test
  @DisplayName("Valkey desabilitado deve usar sticky local")
  void valkeyDesabilitadoDeveUsarStickyLocal() {
    UUID sessionId = UUID.randomUUID();
    StickyWriterService service = service(true, false, 10);

    assertFalse(service.requiresWriter(sessionId));
    service.markWriter(sessionId);
    assertTrue(service.requiresWriter(sessionId));

    verifyNoInteractions(redisTemplate);
    assertMetric(serviceRegistry, "inactive", 1.0);
    assertMetric(serviceRegistry, "marked", 1.0);
    assertMetric(serviceRegistry, "active", 1.0);
  }

  @Test
  @DisplayName("TTL invalido deve falhar configuracao")
  void ttlInvalidoDeveFalharConfiguracao() {
    assertThrows(IllegalArgumentException.class, () -> createServiceWithTtl(0));
    verify(redisTemplate, never()).opsForValue();
  }

  private SimpleMeterRegistry serviceRegistry;

  private StickyWriterService service(boolean enabled, long ttlSeconds) {
    return service(enabled, true, ttlSeconds);
  }

  private StickyWriterService service(boolean enabled, boolean valkeyEnabled, long ttlSeconds) {
    return service(enabled, valkeyEnabled, ttlSeconds, new AtomicLong(1_000L));
  }

  private StickyWriterService service(boolean enabled, long ttlSeconds, AtomicLong epochSeconds) {
    return service(enabled, true, ttlSeconds, epochSeconds);
  }

  private StickyWriterService service(
      boolean enabled, boolean valkeyEnabled, long ttlSeconds, AtomicLong epochSeconds) {
    serviceRegistry = new SimpleMeterRegistry();
    return new StickyWriterService(
        redisTemplate,
        serviceRegistry,
        localCache(ttlSeconds),
        ttlSeconds,
        enabled,
        valkeyEnabled,
        "SC_DB_STICKY",
        "0123456789ABCDEF0123456789ABCDEF",
        epochSeconds::get);
  }

  private StickyWriterService createServiceWithTtl(long ttlSeconds) {
    return new StickyWriterService(
        redisTemplate,
        new SimpleMeterRegistry(),
        localCache(1),
        ttlSeconds,
        true,
        true,
        "SC_DB_STICKY",
        "0123456789ABCDEF0123456789ABCDEF",
        () -> 1_000L);
  }

  private Cache<UUID, Boolean> localCache(long ttlSeconds) {
    return Caffeine.newBuilder()
        .maximumSize(100L)
        .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
        .build();
  }

  private void assertMetric(SimpleMeterRegistry registry, String result, double expected) {
    Counter counter =
        registry.find(StickyWriterService.STICKY_METRIC).tag("result", result).counter();
    org.junit.jupiter.api.Assertions.assertNotNull(counter);
    org.junit.jupiter.api.Assertions.assertEquals(expected, counter.count());
  }
}
