package com.sistema_contabilidade.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LocalRateLimitService unit tests")
class LocalRateLimitServiceTest {

  private static final Clock TEST_CLOCK =
      Clock.fixed(Instant.parse("2026-07-26T12:00:00Z"), ZoneOffset.UTC);

  @Test
  @DisplayName("Deve rejeitar acima do limite e recuperar depois da janela")
  void deveRejeitarERecuperarDepoisDaJanela() {
    MutableClock clock = new MutableClock();
    LocalRateLimitService service = new LocalRateLimitService(2, 60, clock);

    assertThat(service.tryAcquire("bucket")).isEqualTo(RateLimitDecision.ALLOWED);
    assertThat(service.tryAcquire("bucket")).isEqualTo(RateLimitDecision.ALLOWED);
    assertThat(service.tryAcquire("bucket")).isEqualTo(RateLimitDecision.REJECTED);

    clock.advance(Duration.ofSeconds(60));

    assertThat(service.tryAcquire("bucket")).isEqualTo(RateLimitDecision.ALLOWED);
  }

  @Test
  @DisplayName("Buckets diferentes devem manter janelas isoladas")
  void bucketsDiferentesDevemManterJanelasIsoladas() {
    LocalRateLimitService service = new LocalRateLimitService(1, 60, TEST_CLOCK);

    assertThat(service.tryAcquire("first")).isEqualTo(RateLimitDecision.ALLOWED);
    assertThat(service.tryAcquire("first")).isEqualTo(RateLimitDecision.REJECTED);
    assertThat(service.tryAcquire("second")).isEqualTo(RateLimitDecision.ALLOWED);
  }

  @Test
  @DisplayName("Concorrencia local nao deve liberar acima do limite")
  void concorrenciaLocalNaoDeveLiberarAcimaDoLimite() {
    LocalRateLimitService service = new LocalRateLimitService(20, 60, TEST_CLOCK);

    long allowed =
        IntStream.range(0, 100)
            .parallel()
            .mapToObj(ignored -> service.tryAcquire("shared"))
            .filter(RateLimitDecision.ALLOWED::equals)
            .count();

    assertThat(allowed).isEqualTo(20);
  }

  @Test
  @DisplayName("Configuracao invalida deve falhar no startup")
  void configuracaoInvalidaDeveFalharNoStartup() {
    Clock clock = TEST_CLOCK;

    assertThatThrownBy(() -> new LocalRateLimitService(0, 60, clock))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LocalRateLimitService(1, 0, clock))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static final class MutableClock extends Clock {

    private Instant instant = Instant.parse("2026-07-26T12:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }

    private void advance(Duration duration) {
      instant = instant.plus(duration);
    }
  }
}
