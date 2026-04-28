package com.sistema_contabilidade.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.storage")
@Getter
@Setter
public class StorageProperties {

  private String type = "local";
  private long maxPdfSizeBytes = 20L * 1024L * 1024L;
  private LocalProperties local = new LocalProperties();
  private S3Properties s3 = new S3Properties();

  @Getter
  @Setter
  public static class LocalProperties {
    private String baseDir = "uploads/itens";
  }

  @Getter
  @Setter
  public static class S3Properties {
    private String bucket;
    private String region = "us-east-1";
    private String endpoint;
    private String prefix = "itens";
  }
}
