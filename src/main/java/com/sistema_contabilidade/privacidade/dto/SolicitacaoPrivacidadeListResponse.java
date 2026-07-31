package com.sistema_contabilidade.privacidade.dto;

import java.util.List;

public record SolicitacaoPrivacidadeListResponse(
    List<SolicitacaoPrivacidadeResponse> solicitacoes,
    int pagina,
    int tamanho,
    long totalElementos,
    int totalPaginas,
    SolicitacaoPrivacidadeResumoResponse resumo) {

  public SolicitacaoPrivacidadeListResponse {
    solicitacoes = List.copyOf(solicitacoes);
  }
}
