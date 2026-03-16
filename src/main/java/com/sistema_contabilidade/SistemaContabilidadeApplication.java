package com.sistema_contabilidade;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableCaching
public class SistemaContabilidadeApplication {

  @Bean
  CacheManager cacheManager() {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager("userDetails");
    cacheManager.setCaffeine(
        Caffeine.newBuilder().maximumSize(500).expireAfterWrite(Duration.ofMinutes(5)));
    return cacheManager;
  }

  public static void main(String[] args) {
    SpringApplication.run(SistemaContabilidadeApplication.class, args);
  }
}
