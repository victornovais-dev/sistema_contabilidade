package com.sistema_contabilidade.item.dto;

import tools.jackson.databind.JsonNode;

public record ItemAuditoriaAlteracaoResponse(JsonNode antes, JsonNode depois) {

  public ItemAuditoriaAlteracaoResponse {
    antes = copiar(antes);
    depois = copiar(depois);
  }

  @Override
  public JsonNode antes() {
    return copiar(antes);
  }

  @Override
  public JsonNode depois() {
    return copiar(depois);
  }

  private static JsonNode copiar(JsonNode valor) {
    return valor == null ? null : valor.deepCopy();
  }
}
