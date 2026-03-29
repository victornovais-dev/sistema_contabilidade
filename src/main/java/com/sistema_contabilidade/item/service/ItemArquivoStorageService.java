package com.sistema_contabilidade.item.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ItemArquivoStorageService {

  private static final String PDF_EXTENSION = ".pdf";
  private static final int MAX_NOME_ARQUIVO = 120;

  private final Path diretorioArquivos;

  public ItemArquivoStorageService(
      @Value("${app.item.arquivos-dir:uploads/itens}") String diretorioArquivos) {
    this.diretorioArquivos = Path.of(diretorioArquivos);
  }

  public String salvarPdf(byte[] arquivoPdf) {
    return salvarPdf(arquivoPdf, null);
  }

  public String salvarPdf(byte[] arquivoPdf, String nomeOriginal) {
    try {
      Files.createDirectories(diretorioArquivos);
      String nomeSanitizado = sanitizarNomeArquivo(nomeOriginal);
      Path arquivo = diretorioArquivos.resolve(nomeSanitizado);
      if (Files.exists(arquivo)) {
        String nomeComSuffix = aplicarSuffix(nomeSanitizado);
        arquivo = diretorioArquivos.resolve(nomeComSuffix);
      }
      Files.write(arquivo, arquivoPdf, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
      return arquivo.toString();
    } catch (IOException ex) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao salvar arquivo PDF", ex);
    }
  }

  public List<String> salvarPdfs(List<byte[]> arquivosPdf, List<String> nomesArquivos) {
    if (arquivosPdf == null || arquivosPdf.isEmpty()) {
      return List.of();
    }
    List<String> caminhos = new ArrayList<>();
    for (int index = 0; index < arquivosPdf.size(); index += 1) {
      byte[] arquivoPdf = arquivosPdf.get(index);
      String nomeOriginal =
          nomesArquivos != null && index < nomesArquivos.size() ? nomesArquivos.get(index) : null;
      caminhos.add(salvarPdf(arquivoPdf, nomeOriginal));
    }
    return caminhos;
  }

  public List<String> salvarPdfs(List<byte[]> arquivosPdf) {
    return salvarPdfs(arquivosPdf, null);
  }

  private String sanitizarNomeArquivo(String nomeOriginal) {
    if (nomeOriginal == null || nomeOriginal.isBlank()) {
      return UUID.randomUUID() + PDF_EXTENSION;
    }
    String nome = nomeOriginal.trim().replace(" ", "_").replace("-", "_");
    nome = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    int lastSlash = Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\'));
    if (lastSlash >= 0) {
      nome = nome.substring(lastSlash + 1);
    }
    nome = nome.replaceAll("[^A-Za-z0-9._]", "_");
    if (!nome.toLowerCase(Locale.ROOT).endsWith(PDF_EXTENSION)) {
      nome = nome + PDF_EXTENSION;
    }
    if (nome.length() > MAX_NOME_ARQUIVO) {
      nome = nome.substring(0, MAX_NOME_ARQUIVO);
    }
    return nome.isBlank() ? UUID.randomUUID() + PDF_EXTENSION : nome;
  }

  private String aplicarSuffix(String nome) {
    int dot = nome.lastIndexOf('.');
    String base = dot > 0 ? nome.substring(0, dot) : nome;
    String ext = dot > 0 ? nome.substring(dot) : "";
    return base + "_" + UUID.randomUUID() + ext;
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
