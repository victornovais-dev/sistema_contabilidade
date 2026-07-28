package com.sistema_contabilidade.duvida.dto;

import java.util.List;

public record DuvidaListResponse(
    List<DuvidaResponse> duvidas, int pagina, int tamanho, long totalElementos, int totalPaginas) {

  public DuvidaListResponse {
    duvidas = List.copyOf(duvidas);
  }
}
