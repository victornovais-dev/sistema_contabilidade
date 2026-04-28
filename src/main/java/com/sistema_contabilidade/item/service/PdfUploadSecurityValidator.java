package com.sistema_contabilidade.item.service;

import com.sistema_contabilidade.config.StorageProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Slf4j
public class PdfUploadSecurityValidator {

  private static final byte[] PDF_SIGNATURE =
      new byte[] {(byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46, (byte) 0x2D};
  private static final long DEFAULT_MAX_PDF_SIZE_BYTES = 20L * 1024L * 1024L;
  private static final String INVALID_PDF_MESSAGE = "Arquivo PDF invalido.";
  private static final String PDF_ONLY_MESSAGE = "Envie somente arquivos PDF.";
  private static final String PDF_EXECUTABLE_MESSAGE =
      "PDF com conteudo executavel nao e permitido.";
  private static final Set<String> DANGEROUS_EXTENSIONS =
      Set.of(
          "php", "php3", "php4", "php5", "phtml", "phar", "jsp", "jspx", "asp", "aspx", "exe",
          "bat", "cmd", "sh", "bash", "ps1", "svg", "js", "jar", "zip");
  private static final List<String> MALICIOUS_PDF_MARKERS =
      List.of("/JS", "/JavaScript", "/OpenAction", "/Launch", "/RichMedia", "/XFA");
  private static final List<String> DANGEROUS_ADDITIONAL_ACTION_MARKERS =
      List.of("/JS", "/JavaScript", "/Launch", "/SubmitForm", "/ImportData", "/URI");

  private final long maxPdfSizeBytes;

  @Autowired
  public PdfUploadSecurityValidator(StorageProperties storageProperties) {
    this(
        storageProperties == null
            ? DEFAULT_MAX_PDF_SIZE_BYTES
            : storageProperties.getMaxPdfSizeBytes());
  }

  PdfUploadSecurityValidator(long maxPdfSizeBytes) {
    this.maxPdfSizeBytes = maxPdfSizeBytes > 0 ? maxPdfSizeBytes : DEFAULT_MAX_PDF_SIZE_BYTES;
  }

  public void validateUpload(byte[] arquivoPdf, String nomeOriginal) {
    if (arquivoPdf == null || arquivoPdf.length == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_PDF_MESSAGE);
    }
    if (arquivoPdf.length > maxPdfSizeBytes) {
      throw new ResponseStatusException(
          HttpStatus.CONTENT_TOO_LARGE,
          "Arquivo excede o tamanho maximo permitido de " + maxPdfSizeBytes + " bytes.");
    }
    validarNomeArquivo(nomeOriginal);
    validarMagicBytes(arquivoPdf);
    validarMarcadoresMaliciosos(arquivoPdf, nomeOriginal);
  }

  private void validarNomeArquivo(String nomeOriginal) {
    String ext = extrairExtensao(nomeOriginal);
    if (ext.isBlank()) {
      return;
    }
    if (DANGEROUS_EXTENSIONS.contains(ext)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PDF_ONLY_MESSAGE);
    }
    if (!"pdf".equals(ext)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PDF_ONLY_MESSAGE);
    }
  }

  private void validarMagicBytes(byte[] arquivoPdf) {
    if (arquivoPdf.length < PDF_SIGNATURE.length) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_PDF_MESSAGE);
    }
    for (int index = 0; index < PDF_SIGNATURE.length; index += 1) {
      if (arquivoPdf[index] != PDF_SIGNATURE[index]) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_PDF_MESSAGE);
      }
    }
  }

  private void validarMarcadoresMaliciosos(byte[] arquivoPdf, String nomeOriginal) {
    String conteudo = new String(arquivoPdf, StandardCharsets.ISO_8859_1);
    for (String marcador : MALICIOUS_PDF_MARKERS) {
      if (!conteudo.contains(marcador)) {
        continue;
      }
      if (log.isWarnEnabled()) {
        log.warn(
            "Upload PDF bloqueado por marcador suspeito | nomeOriginal: {} | marcador: {}",
            nomeOriginal,
            marcador);
      }
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PDF_EXECUTABLE_MESSAGE);
    }
    validarAdditionalActionsPerigosas(conteudo, nomeOriginal);
  }

  private void validarAdditionalActionsPerigosas(String conteudo, String nomeOriginal) {
    if (!conteudo.contains("/AA")) {
      return;
    }
    for (String marcador : DANGEROUS_ADDITIONAL_ACTION_MARKERS) {
      if (!conteudo.contains(marcador)) {
        continue;
      }
      if (log.isWarnEnabled()) {
        log.warn(
            "Upload PDF bloqueado por additional action suspeita | nomeOriginal: {} | marcador: {}",
            nomeOriginal,
            marcador);
      }
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PDF_EXECUTABLE_MESSAGE);
    }
  }

  private String extrairExtensao(String nomeOriginal) {
    if (nomeOriginal == null || nomeOriginal.isBlank()) {
      return "";
    }
    String nome = nomeOriginal.replace("\0", "").trim();
    try {
      Path fileName = Path.of(nome).getFileName();
      nome = fileName == null ? nome : fileName.toString();
    } catch (InvalidPathException _) {
      int ultimaBarra = Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\'));
      nome = ultimaBarra >= 0 ? nome.substring(ultimaBarra + 1) : nome;
    }
    int ultimoPonto = nome.lastIndexOf('.');
    if (ultimoPonto < 0 || ultimoPonto == nome.length() - 1) {
      return "";
    }
    return nome.substring(ultimoPonto + 1).toLowerCase(Locale.ROOT);
  }
}
