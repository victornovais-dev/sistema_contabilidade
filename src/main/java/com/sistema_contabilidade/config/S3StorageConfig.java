package com.sistema_contabilidade.config;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageConfig {

  private final StorageProperties storageProperties;

  @Bean
  S3Client s3Client() {
    StorageProperties.S3Properties s3 = storageProperties.getS3();

    S3ClientBuilder builder =
        S3Client.builder()
            .region(Region.of(s3.getRegion()))
            .credentialsProvider(DefaultCredentialsProvider.create());

    if (s3.getEndpoint() != null && !s3.getEndpoint().isBlank()) {
      builder.endpointOverride(URI.create(s3.getEndpoint()));
      builder.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
      if (log.isInfoEnabled()) {
        log.info("S3 client configurado com endpoint customizado: {}", s3.getEndpoint());
      }
    }

    if (log.isInfoEnabled()) {
      log.info("S3 client configurado para bucket {} na regiao {}", s3.getBucket(), s3.getRegion());
    }
    return builder.build();
  }
}
