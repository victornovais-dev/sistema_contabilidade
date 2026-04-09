package com.sistema_contabilidade.item.service;

import java.util.List;

public interface ArquivoStorageService {

  String salvarPdf(byte[] arquivoPdf);

  String salvarPdf(byte[] arquivoPdf, String nomeOriginal);

  List<String> salvarPdfs(List<byte[]> arquivosPdf, List<String> nomesArquivos);

  default List<String> salvarPdfs(List<byte[]> arquivosPdf) {
    return salvarPdfs(arquivosPdf, null);
  }

  byte[] carregarPdf(String chaveArquivo);

  void deletarPdf(String chaveArquivo);

  default String resolverNomeArquivo(String chaveArquivo) {
    if (chaveArquivo == null || chaveArquivo.isBlank()) {
      return "arquivo.pdf";
    }
    String chaveNormalizada = chaveArquivo.replace('\\', '/');
    int ultimaBarra = chaveNormalizada.lastIndexOf('/');
    return ultimaBarra >= 0 ? chaveNormalizada.substring(ultimaBarra + 1) : chaveNormalizada;
  }
}
