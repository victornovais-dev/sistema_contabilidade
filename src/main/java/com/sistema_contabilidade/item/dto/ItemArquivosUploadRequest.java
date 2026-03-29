package com.sistema_contabilidade.item.dto;

import java.util.List;

public record ItemArquivosUploadRequest(List<byte[]> arquivosPdf, List<String> nomesArquivos) {

  public ItemArquivosUploadRequest {
    arquivosPdf = copyBytes(arquivosPdf);
    nomesArquivos = nomesArquivos == null ? List.of() : List.copyOf(nomesArquivos);
  }

  @Override
  public List<byte[]> arquivosPdf() {
    return copyBytes(arquivosPdf);
  }

  @Override
  public List<String> nomesArquivos() {
    return nomesArquivos == null ? List.of() : List.copyOf(nomesArquivos);
  }

  private static List<byte[]> copyBytes(List<byte[]> source) {
    if (source == null) {
      return List.of();
    }
    return source.stream().map(bytes -> bytes == null ? null : bytes.clone()).toList();
  }
}
