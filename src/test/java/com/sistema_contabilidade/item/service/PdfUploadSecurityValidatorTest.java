package com.sistema_contabilidade.item.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sistema_contabilidade.config.StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("PdfUploadSecurityValidator unit tests")
class PdfUploadSecurityValidatorTest {

  @Test
  @DisplayName("Deve aceitar PDF valido")
  void deveAceitarPdfValido() {
    PdfUploadSecurityValidator validator = new PdfUploadSecurityValidator(new StorageProperties());

    assertDoesNotThrow(() -> validator.validateUpload(pdfValido(), "comprovante.pdf"));
  }

  @Test
  @DisplayName("Deve rejeitar arquivo sem assinatura PDF")
  void deveRejeitarArquivoSemAssinaturaPdf() {
    PdfUploadSecurityValidator validator = new PdfUploadSecurityValidator(new StorageProperties());
    byte[] arquivoInvalido = "nao-e-pdf".getBytes();

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> validator.validateUpload(arquivoInvalido, "comprovante.pdf"));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  @DisplayName("Deve rejeitar extensao perigosa mesmo com bytes validos")
  void deveRejeitarExtensaoPerigosaMesmoComBytesValidos() {
    PdfUploadSecurityValidator validator = new PdfUploadSecurityValidator(new StorageProperties());
    byte[] arquivoPdf = pdfValido();

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> validator.validateUpload(arquivoPdf, "comprovante.php"));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  @DisplayName("Deve rejeitar PDF com JavaScript embutido")
  void deveRejeitarPdfComJavaScriptEmbutido() {
    PdfUploadSecurityValidator validator = new PdfUploadSecurityValidator(new StorageProperties());
    byte[] pdfComJavascript =
        "%PDF-1.7\n1 0 obj\n<< /OpenAction 2 0 R /JS (alert('x')) >>\n%%EOF".getBytes();

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> validator.validateUpload(pdfComJavascript, "comprovante.pdf"));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  @DisplayName("Deve aceitar PDF com AA sem acao perigosa")
  void deveAceitarPdfComAaSemAcaoPerigosa() {
    PdfUploadSecurityValidator validator = new PdfUploadSecurityValidator(new StorageProperties());
    byte[] pdfComAaSemPerigo =
        "%PDF-1.7\n1 0 obj\n<< /Type /Annot /AA << /E 2 0 R >> >>\nendobj\n%%EOF".getBytes();

    assertDoesNotThrow(() -> validator.validateUpload(pdfComAaSemPerigo, "sentenca.pdf"));
  }

  @Test
  @DisplayName("Deve rejeitar PDF com AA apontando para acao perigosa")
  void deveRejeitarPdfComAaApontandoParaAcaoPerigosa() {
    PdfUploadSecurityValidator validator = new PdfUploadSecurityValidator(new StorageProperties());
    byte[] pdfComAaPerigoso =
        """
        %PDF-1.7
        1 0 obj
        << /Type /Annot /AA << /E 2 0 R >> >>
        endobj
        2 0 obj
        << /S /URI /URI (https://malicioso.exemplo) >>
        endobj
        %%EOF
        """
            .getBytes();

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> validator.validateUpload(pdfComAaPerigoso, "comprovante.pdf"));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  @DisplayName("Deve rejeitar arquivo acima do tamanho maximo")
  void deveRejeitarArquivoAcimaDoTamanhoMaximo() {
    StorageProperties storageProperties = new StorageProperties();
    storageProperties.setMaxPdfSizeBytes(8L);
    PdfUploadSecurityValidator validator = new PdfUploadSecurityValidator(storageProperties);
    byte[] arquivoPdf = pdfValido();

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> validator.validateUpload(arquivoPdf, "comprovante.pdf"));

    assertEquals(HttpStatus.CONTENT_TOO_LARGE, ex.getStatusCode());
  }

  private static byte[] pdfValido() {
    return "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF".getBytes();
  }
}
