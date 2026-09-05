package com.sistema_contabilidade.item.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sistema_contabilidade.item.model.ItemAuditoriaAcao;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@DisplayName("ItemAuditoria DTO unit tests")
class ItemAuditoriaDtoTest {

  @Test
  @DisplayName("Deve preservar ordem e fazer copia defensiva das alteracoes")
  void devePreservarOrdemEFazerCopiaDefensivaDasAlteracoes() {
    ObjectMapper objectMapper = new ObjectMapper();
    Map<String, ItemAuditoriaAlteracaoResponse> alteracoes = new LinkedHashMap<>();
    alteracoes.put(
        "valor",
        new ItemAuditoriaAlteracaoResponse(
            objectMapper.valueToTree(100), objectMapper.valueToTree(120)));
    alteracoes.put(
        "descricao",
        new ItemAuditoriaAlteracaoResponse(
            objectMapper.valueToTree("ANTIGA"), objectMapper.valueToTree("NOVA")));

    ItemAuditoriaResponse response =
        new ItemAuditoriaResponse(
            UUID.fromString("70000000-0000-0000-0000-000000000001"),
            ItemAuditoriaAcao.ALTERADO,
            "Auditor Teste",
            Instant.parse("2026-09-04T15:00:00Z"),
            alteracoes);
    alteracoes.clear();

    assertEquals(2, response.alteracoes().size());
    assertEquals("valor", response.alteracoes().keySet().iterator().next());
    Map<String, ItemAuditoriaAlteracaoResponse> alteracoesImutaveis = response.alteracoes();
    ItemAuditoriaAlteracaoResponse alteracaoTipo =
        new ItemAuditoriaAlteracaoResponse(
            objectMapper.valueToTree("DESPESA"), objectMapper.valueToTree("RECEITA"));
    assertThrows(
        UnsupportedOperationException.class, () -> alteracoesImutaveis.put("tipo", alteracaoTipo));
  }

  @Test
  @DisplayName("Deve normalizar alteracoes ausentes para mapa vazio")
  void deveNormalizarAlteracoesAusentesParaMapaVazio() {
    ItemAuditoriaResponse response =
        new ItemAuditoriaResponse(
            UUID.randomUUID(),
            ItemAuditoriaAcao.CRIADO,
            "Auditor Teste",
            Instant.parse("2026-09-04T15:00:00Z"),
            null);

    assertEquals(Map.of(), response.alteracoes());
  }
}
