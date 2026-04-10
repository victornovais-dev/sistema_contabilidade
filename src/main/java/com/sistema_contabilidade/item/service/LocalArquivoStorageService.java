package com.sistema_contabilidade.item.service;

import com.sistema_contabilidade.config.StorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalArquivoStorageService implements ArquivoStorageService {

  private final Path diretorioArquivos;

  @Autowired
  public LocalArquivoStorageService(StorageProperties storageProperties) {
    this(storageProperties.getLocal().getBaseDir());
  }

  LocalArquivoStorageService(String diretorioArquivos) {
    this.diretorioArquivos = Path.of(diretorioArquivos);
  }

  @Override
  public String salvarPdf(byte[] arquivoPdf) {
    return salvarPdf(arquivoPdf, null);
  }

  @Override
  public String salvarPdf(byte[] arquivoPdf, String nomeOriginal) {
    try {
      Files.createDirectories(diretorioArquivos);
      String nomeSanitizado = ArquivoStorageNamingUtils.gerarNomeSanitizado(nomeOriginal);
      Path arquivo = diretorioArquivos.resolve(nomeSanitizado);
      if (Files.exists(arquivo)) {
        String nomeComSuffix = ArquivoStorageNamingUtils.aplicarSuffix(nomeSanitizado);
        arquivo = diretorioArquivos.resolve(nomeComSuffix);
      }
      Files.write(arquivo, arquivoPdf, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
      return arquivo.toString();
    } catch (IOException ex) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao salvar arquivo PDF", ex);
    }
  }

  @Override
  public List<String> salvarPdfs(List<byte[]> arquivosPdf, List<String> nomesArquivos) {
    if (arquivosPdf == null || arquivosPdf.isEmpty()) {
      return List.of();
    }
    List<String> caminhos = new ArrayList<>();
    try {
      for (int index = 0; index < arquivosPdf.size(); index += 1) {
        byte[] arquivoPdf = arquivosPdf.get(index);
        String nomeOriginal =
            nomesArquivos != null && index < nomesArquivos.size() ? nomesArquivos.get(index) : null;
        caminhos.add(salvarPdf(arquivoPdf, nomeOriginal));
      }
      return caminhos;
    } catch (RuntimeException ex) {
      for (String caminho : caminhos) {
        deletarPdf(caminho);
      }
      throw ex;
    }
  }

  @Override
  public byte[] carregarPdf(String caminhoArquivoPdf) {
    if (caminhoArquivoPdf == null || caminhoArquivoPdf.isBlank()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo PDF nao encontrado");
    }

    Path caminhoInformado = Path.of(caminhoArquivoPdf).normalize();
    Path caminhoBase = diretorioArquivos.toAbsolutePath().normalize();
    Path caminhoReal = caminhoInformado.toAbsolutePath().normalize();

    if (!caminhoReal.startsWith(caminhoBase)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Caminho de arquivo invalido");
    }

    if (!Files.exists(caminhoReal) || !Files.isRegularFile(caminhoReal)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo PDF nao encontrado");
    }

    try {
      return Files.readAllBytes(caminhoReal);
    } catch (IOException ex) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao ler arquivo PDF", ex);
    }
  }

  @Override
  public void deletarPdf(String caminhoArquivoPdf) {
    if (caminhoArquivoPdf == null || caminhoArquivoPdf.isBlank()) {
      return;
    }

    Path caminhoInformado = Path.of(caminhoArquivoPdf).normalize();
    Path caminhoBase = diretorioArquivos.toAbsolutePath().normalize();
    Path caminhoReal = caminhoInformado.toAbsolutePath().normalize();

    if (!caminhoReal.startsWith(caminhoBase)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Caminho de arquivo invalido");
    }

    try {
      Files.deleteIfExists(caminhoReal);
    } catch (IOException ex) {
      log.warn("Falha ao deletar PDF no filesystem: {}", caminhoReal, ex);
    }
  }
}
