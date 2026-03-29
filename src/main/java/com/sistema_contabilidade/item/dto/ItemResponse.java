package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ItemResponse(
    UUID id,
    BigDecimal valor,
    LocalDate data,
    LocalDateTime horarioCriacao,
    String caminhoArquivoPdf,
    TipoItem tipo,
    String descricao,
    String razaoSocialNome,
    String cnpjCpf,
    String observacao,
    List<String> arquivosPdf) {

  public ItemResponse {
    arquivosPdf = arquivosPdf == null ? List.of() : List.copyOf(arquivosPdf);
  }

  @Override
  public List<String> arquivosPdf() {
    return arquivosPdf == null ? List.of() : List.copyOf(arquivosPdf);
  }

  public static ItemResponse from(Item item) {
    return new ItemResponse(
        item.getId(),
        item.getValor(),
        item.getData(),
        item.getHorarioCriacao(),
        item.getCaminhoArquivoPdf(),
        item.getTipo(),
        item.getDescricao(),
        item.getRazaoSocialNome(),
        item.getCnpjCpf(),
        item.getObservacao(),
        item.getArquivos().stream().map(arquivo -> arquivo.getCaminhoArquivoPdf()).toList());
  }
}
