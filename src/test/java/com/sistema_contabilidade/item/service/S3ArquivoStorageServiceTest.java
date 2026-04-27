package com.sistema_contabilidade.item.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.config.StorageProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

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
  @DisplayName("Deve usar sobrecarga simples de upload")
  void deveUsarSobrecargaSimplesDeUpload() {
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());

    String chave = service.salvarPdf("conteudo".getBytes());

    assertTrue(chave.startsWith("itens/"));
    verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  @DisplayName("Deve normalizar prefixo antes do upload")
  void deveNormalizarPrefixoAntesDoUpload() {
    storageProperties.getS3().setPrefix(" /financeiro\\mensal// ");
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());
    ArgumentCaptor<PutObjectRequest> requestCaptor =
        ArgumentCaptor.forClass(PutObjectRequest.class);

    String chave = service.salvarPdf("conteudo".getBytes(), "arquivo.pdf");

    verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
    assertTrue(chave.startsWith("financeiro/mensal/"));
    assertTrue(requestCaptor.getValue().key().startsWith("financeiro/mensal/"));
  }

  @Test
  @DisplayName("Deve usar prefixo padrao quando prefixo estiver vazio")
  void deveUsarPrefixoPadraoQuandoPrefixoEstiverVazio() {
    storageProperties.getS3().setPrefix("///");
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());

    String chave = service.salvarPdf("conteudo".getBytes(), "arquivo.pdf");

    assertTrue(chave.startsWith("itens/"));
  }

  @Test
  @DisplayName("Deve retornar lista vazia quando nao houver arquivos")
  void deveRetornarListaVaziaQuandoNaoHouverArquivos() {
    assertEquals(List.of(), service.salvarPdfs(null, null));
    assertEquals(List.of(), service.salvarPdfs(List.of(), List.of()));
    verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  @DisplayName("Deve remover uploads parciais quando lote falhar")
  void deveRemoverUploadsParciaisQuandoLoteFalhar() {
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build())
        .thenThrow(SdkClientException.create("s3 indisponivel"));
    when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenReturn(DeleteObjectResponse.builder().build());
    List<byte[]> arquivos = List.of("a".getBytes(), "b".getBytes());

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> service.salvarPdfs(arquivos, null));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
  }

  @Test
  @DisplayName("Deve retornar chaves quando lote concluir com nomes informados")
  void deveRetornarChavesQuandoLoteConcluirComNomesInformados() {
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());

    List<String> chaves =
        service.salvarPdfs(
            List.of("a".getBytes(), "b".getBytes()), List.of("primeiro.pdf", "segundo.pdf"));

    assertEquals(2, chaves.size());
    assertTrue(chaves.get(0).endsWith("/primeiro.pdf"));
    assertTrue(chaves.get(1).endsWith("/segundo.pdf"));
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
  @DisplayName("Deve retornar 404 quando chave estiver vazia")
  void deveRetornar404QuandoChaveEstiverVazia() {
    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> service.carregarPdf(" "));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  @DisplayName("Deve traduzir S3Exception 404 ao carregar PDF")
  void deveTraduzirS3Exception404AoCarregarPdf() {
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenThrow(S3Exception.builder().statusCode(HttpStatus.NOT_FOUND.value()).build());

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> service.carregarPdf("itens/id/nao-encontrado"));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  @DisplayName("Deve traduzir falha de cliente S3 ao carregar PDF")
  void deveTraduzirFalhaDeClienteS3AoCarregarPdf() {
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenThrow(SdkClientException.create("offline"));

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> service.carregarPdf("itens/id/arquivo.pdf"));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
  }

  @Test
  @DisplayName("Deve traduzir erro S3 inesperado ao carregar PDF")
  void deveTraduzirErroS3InesperadoAoCarregarPdf() {
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenThrow(
            S3Exception.builder()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .awsErrorDetails(
                    software.amazon.awssdk.awscore.exception.AwsErrorDetails.builder()
                        .errorCode("boom")
                        .build())
                .build());

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> service.carregarPdf("itens/id/arquivo.pdf"));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
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
  @DisplayName("Deve ignorar exclusao sem chave")
  void deveIgnorarExclusaoSemChave() {
    service.deletarPdf(" ");

    verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
  }

  @Test
  @DisplayName("Deve ignorar falha ao deletar arquivo no bucket")
  void deveIgnorarFalhaAoDeletarArquivoNoBucket() {
    when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenThrow(SdkClientException.create("indisponivel"));

    service.deletarPdf("itens/id/arquivo.pdf");

    verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
  }

  @Test
  @DisplayName("Deve falhar quando bucket nao estiver configurado")
  void deveFalharQuandoBucketNaoEstiverConfigurado() {
    storageProperties.getS3().setBucket(" ");
    byte[] arquivoPdf = "x".getBytes();

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> service.salvarPdf(arquivoPdf, "arquivo.pdf"));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
  }

  @Test
  @DisplayName("Deve usar prefixo padrao quando prefixo for nulo")
  void deveUsarPrefixoPadraoQuandoPrefixoForNulo() {
    storageProperties.getS3().setPrefix(null);
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());

    String chave = service.salvarPdf("conteudo".getBytes(), "arquivo.pdf");

    assertTrue(chave.startsWith("itens/"));
  }

  @Test
  @DisplayName("Deve traduzir erro S3 no upload")
  void deveTraduzirErroS3NoUpload() {
    byte[] conteudo = "conteudo".getBytes();
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenThrow(
            S3Exception.builder()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .awsErrorDetails(
                    software.amazon.awssdk.awscore.exception.AwsErrorDetails.builder()
                        .errorCode("put_failed")
                        .build())
                .build());

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> service.salvarPdf(conteudo, "arquivo.pdf"));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
  }
}
