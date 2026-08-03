package com.sistema_contabilidade.relatorio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.database.routing.DatabaseRoutingContext;
import com.sistema_contabilidade.rbac.service.CampaignScope;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResumoResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("RelatorioResumoCacheService unit tests")
class RelatorioResumoCacheServiceTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private ObjectMapper objectMapper;
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    meterRegistry = new SimpleMeterRegistry();
    Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @AfterEach
  void clearRoutingContext() {
    DatabaseRoutingContext.clear();
  }

  @Test
  @DisplayName("Deve retornar hit sem consultar banco")
  void deveRetornarHitSemConsultarBanco() throws Exception {
    RelatorioResumoCacheService service = service(true, 30, 0, 131_072);
    RelatorioFinanceiroResumoResponse cached = response("42.00");
    stubVersionAndPayload("7", objectMapper.writeValueAsString(cached));
    AtomicInteger loads = new AtomicInteger();

    RelatorioFinanceiroResumoResponse result =
        service.getOrCompute(
            CampaignScope.restricted(Set.of("CONTABIL")).withRoleFilter("CONTABIL"),
            "role=CONTABIL",
            () -> {
              loads.incrementAndGet();
              return response("99.00");
            });

    assertThat(result).isEqualTo(cached);
    assertThat(loads).hasValue(0);
    assertCacheMetric("hit", 1.0);
    verify(valueOperations, never()).set(anyString(), anyString(), Mockito.any(Duration.class));
  }

  @Test
  @DisplayName("Miss deve gravar JSON com TTL de trinta segundos")
  void missDeveGravarJsonComTtlDeTrintaSegundos() {
    RelatorioResumoCacheService service = service(true, 30, 0, 131_072);
    stubVersionAndPayload("7", null);
    RelatorioFinanceiroResumoResponse computed = response("55.00");

    RelatorioFinanceiroResumoResponse result =
        service.getOrCompute(
            CampaignScope.all().withRoleFilter("CONTABIL"), "role=CONTABIL", () -> computed);

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
    verify(valueOperations)
        .set(keyCaptor.capture(), payloadCaptor.capture(), eq(Duration.ofSeconds(30)));
    assertThat(result).isSameAs(computed);
    assertThat(keyCaptor.getValue())
        .matches("relatorio:resumo:v1:[0-9a-f]{64}:CONTABIL:[0-9a-f]{64}:7");
    assertThat(payloadCaptor.getValue())
        .contains("55.00")
        .doesNotContain("password", "token", "authentication");
    assertCacheMetric("miss", 1.0);
  }

  @Test
  @DisplayName("Chave deve usar o escopo efetivo e isolar filtros")
  void chaveDeveIsolarEscopoRoleEFiltros() {
    RelatorioResumoCacheService service = service(true, 30, 0, 131_072);
    when(valueOperations.get(anyString()))
        .thenAnswer(
            invocation ->
                RelatorioResumoCacheService.VERSION_KEY.equals(invocation.getArgument(0))
                    ? "3"
                    : null);

    service.getOrCompute(
        CampaignScope.all().withRoleFilter("CONTABIL"), "role=CONTABIL", () -> response("1"));
    service.getOrCompute(
        CampaignScope.restricted(Set.of("CONTABIL")).withRoleFilter("CONTABIL"),
        "role=CONTABIL",
        () -> response("1"));
    service.getOrCompute(
        CampaignScope.restricted(Set.of("FINANCEIRO")), "role=ALL", () -> response("1"));

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(valueOperations, Mockito.times(3))
        .set(keyCaptor.capture(), anyString(), eq(Duration.ofSeconds(30)));
    assertThat(keyCaptor.getAllValues().get(0)).isEqualTo(keyCaptor.getAllValues().get(1));
    assertThat(keyCaptor.getAllValues().get(2)).isNotEqualTo(keyCaptor.getAllValues().get(0));
  }

  @Test
  @DisplayName("Mutacao de item deve incrementar versao global")
  void mutacaoDeItemDeveIncrementarVersaoGlobal() {
    RelatorioResumoCacheService service = service(true, 30, 0, 131_072);
    when(valueOperations.increment(RelatorioResumoCacheService.VERSION_KEY)).thenReturn(9L);

    service.invalidateAfterItemWrite();

    verify(valueOperations).increment(RelatorioResumoCacheService.VERSION_KEY);
  }

  @Test
  @DisplayName("Sessao sticky deve ignorar Valkey")
  void sessaoStickyDeveIgnorarValkey() {
    RelatorioResumoCacheService service = service(true, 30, 0, 131_072);
    RelatorioFinanceiroResumoResponse computed = response("73.00");
    DatabaseRoutingContext.forceWriterForSticky();

    RelatorioFinanceiroResumoResponse result =
        service.getOrCompute(CampaignScope.all(), "role=ALL", () -> computed);

    assertThat(result).isSameAs(computed);
    verifyNoInteractions(valueOperations);
    assertCacheMetric("bypass", 1.0);
  }

  @Test
  @DisplayName("Falha Valkey deve calcular uma vez pelo banco")
  void falhaValkeyDeveCalcularUmaVezPeloBanco() {
    RelatorioResumoCacheService service = service(true, 30, 0, 131_072);
    when(valueOperations.get(RelatorioResumoCacheService.VERSION_KEY))
        .thenThrow(new IllegalStateException("offline"));
    AtomicInteger loads = new AtomicInteger();

    RelatorioFinanceiroResumoResponse result =
        service.getOrCompute(
            CampaignScope.all(),
            "role=ALL",
            () -> {
              loads.incrementAndGet();
              return response("88.00");
            });

    assertThat(result.saldoFinal()).isEqualByComparingTo("88.00");
    assertThat(loads).hasValue(1);
    assertCacheMetric("error", 1.0);
    assertValkeyErrorMetric(1.0);
  }

  @Test
  @DisplayName("Payload acima de 128 KiB nao deve ser cacheado")
  void payloadAcimaDoLimiteNaoDeveSerCacheado() {
    RelatorioResumoCacheService service = service(true, 30, 0, 32);
    stubVersionAndPayload("0", null);
    RelatorioFinanceiroResumoResponse computed = response("123456789.00");

    RelatorioFinanceiroResumoResponse result =
        service.getOrCompute(CampaignScope.all(), "role=ALL", () -> computed);

    assertThat(result).isSameAs(computed);
    verify(valueOperations, never()).set(anyString(), anyString(), Mockito.any(Duration.class));
    assertCacheMetric("bypass", 1.0);
  }

  @Test
  @DisplayName("Cache desabilitado deve usar banco sem tocar Redis")
  void cacheDesabilitadoDeveUsarBancoSemTocarRedis() {
    RelatorioResumoCacheService service = service(false, 30, 0, 131_072);
    RelatorioFinanceiroResumoResponse computed = response("15.00");

    RelatorioFinanceiroResumoResponse result =
        service.getOrCompute(CampaignScope.all(), "role=ALL", () -> computed);
    service.invalidateAfterItemWrite();

    assertThat(result).isSameAs(computed);
    verifyNoInteractions(valueOperations);
    assertCacheMetric("bypass", 1.0);
  }

  @Test
  @DisplayName("Configuracao invalida deve falhar no startup")
  void configuracaoInvalidaDeveFalharNoStartup() {
    assertThatThrownBy(() -> service(true, 0, 0, 131_072))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service(true, 31, 0, 131_072))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service(true, 30, 30, 131_072))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service(true, 30, 0, 0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Miss deve aplicar jitter sem ultrapassar trinta segundos")
  void missDeveAplicarJitterSemUltrapassarTrintaSegundos() {
    RelatorioResumoCacheService service = service(true, 30, 3, 131_072);
    stubVersionAndPayload("7", null);

    service.getOrCompute(CampaignScope.all(), "role=ALL", () -> response("55.00"));

    ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(valueOperations).set(anyString(), anyString(), ttlCaptor.capture());
    assertThat(ttlCaptor.getValue()).isBetween(Duration.ofSeconds(27), Duration.ofSeconds(30));
  }

  private void stubVersionAndPayload(String version, String payload) {
    when(valueOperations.get(anyString()))
        .thenAnswer(
            invocation ->
                RelatorioResumoCacheService.VERSION_KEY.equals(invocation.getArgument(0))
                    ? version
                    : payload);
  }

  private RelatorioResumoCacheService service(
      boolean enabled, long ttlSeconds, long jitterSeconds, int maxBytes) {
    return new RelatorioResumoCacheService(
        redisTemplate, objectMapper, meterRegistry, ttlSeconds, jitterSeconds, maxBytes, enabled);
  }

  private RelatorioFinanceiroResumoResponse response(String saldoFinal) {
    return new RelatorioFinanceiroResumoResponse(
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.valueOf(2),
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.valueOf(2),
        BigDecimal.valueOf(2),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ONE,
        BigDecimal.ONE,
        new BigDecimal(saldoFinal),
        BigDecimal.ONE,
        List.of());
  }

  private void assertCacheMetric(String result, double expected) {
    Counter counter =
        meterRegistry
            .find(RelatorioResumoCacheService.CACHE_METRIC)
            .tag("result", result)
            .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(expected);
  }

  private void assertValkeyErrorMetric(double expected) {
    Counter counter =
        meterRegistry
            .find(RelatorioResumoCacheService.VALKEY_ERROR_METRIC)
            .tag("operation", "relatorio_resumo_cache")
            .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(expected);
  }
}
