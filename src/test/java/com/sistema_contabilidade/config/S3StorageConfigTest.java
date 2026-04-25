package com.sistema_contabilidade.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

@DisplayName("S3StorageConfig unit tests")
class S3StorageConfigTest {

  @Test
  @DisplayName("Deve criar cliente S3 com endpoint padrao")
  void deveCriarClienteS3ComEndpointPadrao() {
    StorageProperties storageProperties = storageProperties(null);
    S3StorageConfig config = new S3StorageConfig(storageProperties);

    try (S3Client client = config.s3Client()) {
      assertNotNull(client);
    }
  }

  @Test
  @DisplayName("Deve criar cliente S3 com endpoint customizado")
  void deveCriarClienteS3ComEndpointCustomizado() {
    StorageProperties storageProperties = storageProperties("http://localhost:4566");
    S3StorageConfig config = new S3StorageConfig(storageProperties);

    try (S3Client client = config.s3Client()) {
      assertNotNull(client);
    }
  }

  private StorageProperties storageProperties(String endpoint) {
    StorageProperties storageProperties = new StorageProperties();
    storageProperties.getS3().setBucket("test-bucket");
    storageProperties.getS3().setRegion("us-east-1");
    storageProperties.getS3().setEndpoint(endpoint);
    return storageProperties;
  }
}
