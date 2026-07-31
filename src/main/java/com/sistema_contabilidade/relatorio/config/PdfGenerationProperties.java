package com.sistema_contabilidade.relatorio.config;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "app.pdf")
@Validated
@Getter
@Setter
public class PdfGenerationProperties {

  @Positive private int maxConcurrency = 2;

  @PositiveOrZero private int queueCapacity = 4;

  @Positive private long retryAfterSeconds = 5L;
}
