package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.ItemArquivo;
import java.util.UUID;

public record ItemArquivoResponse(UUID id, String caminhoArquivoPdf) {

  public static ItemArquivoResponse from(ItemArquivo arquivo) {
    return new ItemArquivoResponse(arquivo.getId(), arquivo.getCaminhoArquivoPdf());
  }
}
