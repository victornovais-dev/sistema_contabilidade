package com.sistema_contabilidade.item.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ItemArquivoStorageService {

  private final Path diretorioArquivos;

  public ItemArquivoStorageService(
      @Value("${app.item.arquivos-dir:uploads/itens}") String diretorioArquivos) {
    this.diretorioArquivos = Path.of(diretorioArquivos);
  }

  public String salvarPdf(byte[] arquivoPdf) {
    try {
      Files.createDirectories(diretorioArquivos);
      Path arquivo = diretorioArquivos.resolve(UUID.randomUUID() + ".pdf");
      Files.write(arquivo, arquivoPdf, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
      return arquivo.toString();
    } catch (IOException ex) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao salvar arquivo PDF", ex);
    }
  }

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
}
