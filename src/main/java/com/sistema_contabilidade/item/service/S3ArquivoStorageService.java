package com.sistema_contabilidade.item.service;

import com.sistema_contabilidade.config.StorageProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3ArquivoStorageService implements ArquivoStorageService {

  private final S3Client s3Client;
  private final StorageProperties storageProperties;

  @Override
  public String salvarPdf(byte[] arquivoPdf) {
    return salvarPdf(arquivoPdf, null);
  }

  @Override
  public String salvarPdf(byte[] arquivoPdf, String nomeOriginal) {
    String bucket = getBucket();
    String chave = gerarChave(nomeOriginal);
    try {
      s3Client.putObject(
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(chave)
              .contentType("application/pdf")
              .contentLength((long) arquivoPdf.length)
              .build(),
          RequestBody.fromBytes(arquivoPdf));
      log.info("Upload S3 concluido | bucket: {} | chave: {}", bucket, chave);
      return chave;
    } catch (S3Exception ex) {
      throw erroStorage("Erro ao salvar arquivo PDF no bucket", ex, chave);
    } catch (SdkClientException ex) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Servico de armazenamento temporariamente indisponivel",
          ex);
    }
  }

  @Override
  public List<String> salvarPdfs(List<byte[]> arquivosPdf, List<String> nomesArquivos) {
    if (arquivosPdf == null || arquivosPdf.isEmpty()) {
      return List.of();
    }
    List<String> chaves = new ArrayList<>();
    try {
      for (int index = 0; index < arquivosPdf.size(); index += 1) {
        byte[] arquivoPdf = arquivosPdf.get(index);
        String nomeOriginal =
            nomesArquivos != null && index < nomesArquivos.size() ? nomesArquivos.get(index) : null;
        chaves.add(salvarPdf(arquivoPdf, nomeOriginal));
      }
      return chaves;
    } catch (RuntimeException ex) {
      for (String chave : chaves) {
        deletarPdf(chave);
      }
      throw ex;
    }
  }

  @Override
  public byte[] carregarPdf(String chaveArquivo) {
    if (chaveArquivo == null || chaveArquivo.isBlank()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo PDF nao encontrado");
    }

    try {
      return s3Client
          .getObjectAsBytes(
              GetObjectRequest.builder().bucket(getBucket()).key(chaveArquivo).build())
          .asByteArray();
    } catch (NoSuchKeyException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo PDF nao encontrado", ex);
    } catch (S3Exception ex) {
      if (ex.statusCode() == HttpStatus.NOT_FOUND.value()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo PDF nao encontrado", ex);
      }
      throw erroStorage("Erro ao ler arquivo PDF do bucket", ex, chaveArquivo);
    } catch (SdkClientException ex) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Servico de armazenamento temporariamente indisponivel",
          ex);
    }
  }

  @Override
  public void deletarPdf(String chaveArquivo) {
    if (chaveArquivo == null || chaveArquivo.isBlank()) {
      return;
    }

    try {
      s3Client.deleteObject(
          DeleteObjectRequest.builder().bucket(getBucket()).key(chaveArquivo).build());
      log.info("Arquivo removido do bucket | chave: {}", chaveArquivo);
    } catch (S3Exception | SdkClientException ex) {
      log.warn("Falha ao deletar arquivo do bucket | chave: {}", chaveArquivo, ex);
    }
  }

  private String gerarChave(String nomeOriginal) {
    String prefixo = normalizarPrefixo(storageProperties.getS3().getPrefix());
    String nomeSanitizado = ArquivoStorageNamingUtils.gerarNomeSanitizado(nomeOriginal);
    String id = UUID.randomUUID().toString();
    return prefixo + "/" + id + "/" + nomeSanitizado;
  }

  private String getBucket() {
    String bucket = storageProperties.getS3().getBucket();
    if (bucket == null || bucket.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Bucket de armazenamento nao configurado");
    }
    return bucket;
  }

  private String normalizarPrefixo(String prefixo) {
    if (prefixo == null || prefixo.isBlank()) {
      return "itens";
    }
    return prefixo.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "");
  }

  private ResponseStatusException erroStorage(String mensagem, S3Exception ex, String chave) {
    if (log.isErrorEnabled()) {
      log.error(
          "{} | chave: {} | status: {} | codigo: {}",
          mensagem,
          chave,
          ex.statusCode(),
          ex.awsErrorDetails() == null ? "desconhecido" : ex.awsErrorDetails().errorCode(),
          ex);
    }
    return new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Servico de armazenamento temporariamente indisponivel",
        ex);
  }
}
