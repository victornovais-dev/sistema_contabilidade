package com.sistema_contabilidade.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.database.routing.DatabaseRoutingContext;
import com.sistema_contabilidade.item.dto.ItemListPageResponse;
import com.sistema_contabilidade.item.dto.ItemListResponse;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.rbac.service.CampaignScope;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.UUID;
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
@DisplayName("ItemListPageCacheService unit tests")
class ItemListPageCacheServiceTest {

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
  @DisplayName("Hit deve retornar pagina sem consultar banco")
  void hitDeveRetornarPaginaSemConsultarBanco() throws Exception {
    ItemListPageCacheService service = service(true, 30, 0, 131_072);
    ItemListPageResponse cached = page();
    stubVersionAndPayload("5", objectMapper.writeValueAsString(cached));
    AtomicInteger loads = new AtomicInteger();

    ItemListPageResponse result =
        service.getOrCompute(
            CampaignScope.restricted(java.util.Set.of("FINANCEIRO")),
            "page=1\u001fpageSize=10",
            () -> {
              loads.incrementAndGet();
              return ItemListPageResponse.empty(
                  org.springframework.data.domain.PageRequest.of(0, 10));
            });

    assertThat(result).isEqualTo(cached);
    assertThat(loads).hasValue(0);
    verify(valueOperations, never()).set(anyString(), anyString(), Mockito.any(Duration.class));
    assertCacheMetric("hit", 1.0);
  }

  @Test
  @DisplayName("Miss deve gravar pagina em chave isolada com TTL limitado")
  void missDeveGravarPaginaEmChaveIsoladaComTtlLimitado() {
    ItemListPageCacheService service = service(true, 30, 0, 131_072);
    stubVersionAndPayload("5", null);

    ItemListPageResponse result =
        service.getOrCompute(
            CampaignScope.all().withRoleFilter("FINANCEIRO"),
            "page=1\u001fpageSize=10",
            this::page);

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(valueOperations).set(keyCaptor.capture(), anyString(), eq(Duration.ofSeconds(30)));
    assertThat(result).isEqualTo(page());
    assertThat(keyCaptor.getValue())
        .matches("item:list:page:v1:[0-9a-f]{64}:FINANCEIRO:[0-9a-f]{64}:5");
    assertCacheMetric("miss", 1.0);
  }

  @Test
  @DisplayName("Sessao sticky deve ignorar cache da lista")
  void sessaoStickyDeveIgnorarCacheDaLista() {
    ItemListPageCacheService service = service(true, 30, 0, 131_072);
    DatabaseRoutingContext.forceWriterForSticky();

    ItemListPageResponse result = service.getOrCompute(CampaignScope.all(), "page=1", this::page);

    assertThat(result).isEqualTo(page());
    verifyNoInteractions(valueOperations);
    assertCacheMetric("bypass", 1.0);
  }

  @Test
  @DisplayName("Mutacao de item deve invalidar paginas por versao global")
  void mutacaoDeItemDeveInvalidarPaginasPorVersaoGlobal() {
    ItemListPageCacheService service = service(true, 30, 0, 131_072);
    when(valueOperations.increment(ItemListPageCacheService.VERSION_KEY)).thenReturn(6L);

    service.invalidateAfterItemWrite();

    verify(valueOperations).increment(ItemListPageCacheService.VERSION_KEY);
  }

  @Test
  @DisplayName("Configuracao de TTL fora do limite deve falhar no startup")
  void configuracaoDeTtlForaDoLimiteDeveFalharNoStartup() {
    assertThatThrownBy(() -> service(true, 0, 0, 131_072))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service(true, 31, 0, 131_072))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private void stubVersionAndPayload(String version, String payload) {
    when(valueOperations.get(anyString()))
        .thenAnswer(
            invocation ->
                ItemListPageCacheService.VERSION_KEY.equals(invocation.getArgument(0))
                    ? version
                    : payload);
  }

  private ItemListPageCacheService service(
      boolean enabled, long ttlSeconds, long jitterSeconds, int maxBytes) {
    return new ItemListPageCacheService(
        redisTemplate, objectMapper, meterRegistry, ttlSeconds, jitterSeconds, maxBytes, enabled);
  }

  private ItemListPageResponse page() {
    return new ItemListPageResponse(
        List.of(
            new ItemListResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                new BigDecimal("120.50"),
                LocalDate.of(2026, Month.APRIL, 8),
                LocalDateTime.of(2026, Month.APRIL, 8, 10, 30),
                TipoItem.DESPESA,
                "FINANCEIRO",
                "SERVICOS",
                "EMPRESA TESTE",
                "123.456.789-00",
                false,
                true)),
        1,
        10,
        1,
        1,
        false,
        false,
        null,
        null);
  }

  private void assertCacheMetric(String result, double expected) {
    Counter counter =
        meterRegistry.find(ItemListPageCacheService.CACHE_METRIC).tag("result", result).counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(expected);
  }
}
