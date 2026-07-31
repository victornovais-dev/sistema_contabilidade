package com.sistema_contabilidade.monitoring.cache;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.cache.caffeine")
public class CaffeineCacheProperties {

  @Valid @NotNull private CachePolicy userDetails = new CachePolicy(500L, Duration.ofMinutes(5));

  @Valid @NotNull private CachePolicy itemDescricoes = new CachePolicy(8L, Duration.ofMinutes(5));

  @Valid @NotNull
  private CachePolicy itemTiposDocumento = new CachePolicy(8L, Duration.ofMinutes(5));

  @Valid @NotNull private SizePolicy stickyWriter = new SizePolicy(100_000L);

  @Getter
  @Setter
  public static class CachePolicy {

    @Positive private long maximumSize;
    @NotNull private Duration expireAfterWrite;

    public CachePolicy() {}

    CachePolicy(long maximumSize, Duration expireAfterWrite) {
      this.maximumSize = maximumSize;
      this.expireAfterWrite = expireAfterWrite;
    }

    @AssertTrue(message = "expireAfterWrite deve ser maior que zero")
    public boolean isExpireAfterWritePositive() {
      return expireAfterWrite != null
          && !expireAfterWrite.isZero()
          && !expireAfterWrite.isNegative();
    }
  }

  @Getter
  @Setter
  public static class SizePolicy {

    @Positive private long maximumSize;

    public SizePolicy() {}

    SizePolicy(long maximumSize) {
      this.maximumSize = maximumSize;
    }
  }
}
