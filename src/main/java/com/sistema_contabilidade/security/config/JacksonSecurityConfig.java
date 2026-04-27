package com.sistema_contabilidade.security.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;

@Configuration
public class JacksonSecurityConfig {

  @Bean
  JsonMapperBuilderCustomizer securityJsonMapperBuilderCustomizer() {
    return builder ->
        builder
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }
}
