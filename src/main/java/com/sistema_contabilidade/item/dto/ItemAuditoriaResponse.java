package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.ItemAuditoriaAcao;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record ItemAuditoriaResponse(
    UUID id,
    ItemAuditoriaAcao acao,
    String ator,
    Instant ocorridoEm,
    Map<String, ItemAuditoriaAlteracaoResponse> alteracoes) {

  public ItemAuditoriaResponse {
    alteracoes =
        alteracoes == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(alteracoes));
  }

  @Override
  public Map<String, ItemAuditoriaAlteracaoResponse> alteracoes() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(alteracoes));
  }
}
