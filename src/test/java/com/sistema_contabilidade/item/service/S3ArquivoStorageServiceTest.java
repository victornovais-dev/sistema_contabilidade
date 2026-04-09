package com.sistema_contabilidade.item.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3ArquivoStorageService unit tests")
class S3ArquivoStorageServiceTest {

  @Mock private S3Client s3Client;

  private StorageProperties storageProperties;
  private S3ArquivoStorageService service;

  @BeforeEach
  void setUp() {
    storageProperties = new StorageProperties();
    storageProperties.getS3().setBucket("test-bucket");
    storageProperties.getS3().setPrefix("itens");
    service = new S3ArquivoStorageService(s3Client, storageProperties);
  }

  @Test
  @DisplayName("Deve fazer upload e retornar chave S3")
  void deveFazerUploadERetornarChaveS3() {
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());

    String chave = service.salvarPdf("conteudo".getBytes(), "arquivo.pdf");

    assertTrue(chave.startsWith("itens/"));
    assertTrue(chave.endsWith("/arquivo.pdf"));
    verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  @DisplayName("Deve carregar bytes do bucket")
  @SuppressWarnings("unchecked")
  void deveCarregarBytesDoBucket() {
    byte[] esperado = "pdf".getBytes();
    ResponseBytes<GetObjectResponse> responseBytes = mock(ResponseBytes.class);
    when(responseBytes.asByteArray()).thenReturn(esperado);
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

    byte[] resultado = service.carregarPdf("itens/id/arquivo.pdf");

    assertArrayEquals(esperado, resultado);
  }

  @Test
  @DisplayName("Deve retornar 404 quando chave nao existir")
  void deveRetornar404QuandoChaveNaoExistir() {
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenThrow(NoSuchKeyException.builder().build());

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> service.carregarPdf("itens/id/inexistente.pdf"));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  @DisplayName("Deve chamar delete no bucket")
  void deveChamarDeleteNoBucket() {
    when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenReturn(DeleteObjectResponse.builder().build());

    service.deletarPdf("itens/id/arquivo.pdf");

    verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
  }

  @Test
  @DisplayName("Deve falhar quando bucket nao estiver configurado")
  void deveFalharQuandoBucketNaoEstiverConfigurado() {
    storageProperties.getS3().setBucket(" ");

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> service.salvarPdf("x".getBytes(), "arquivo.pdf"));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
  }
}
