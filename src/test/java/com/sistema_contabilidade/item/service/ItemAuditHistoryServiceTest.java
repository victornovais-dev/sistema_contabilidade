package com.sistema_contabilidade.item.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.item.dto.ItemAuditoriaAlteracaoResponse;
import com.sistema_contabilidade.item.dto.ItemAuditoriaResponse;
import com.sistema_contabilidade.item.model.ItemAuditoria;
import com.sistema_contabilidade.item.model.ItemAuditoriaAcao;
import com.sistema_contabilidade.item.repository.ItemAuditoriaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItemAuditHistoryService unit tests")
class ItemAuditHistoryServiceTest {

  private static final UUID ITEM_ID = UUID.fromString("70000000-0000-0000-0000-000000000002");
  private static final UUID ATOR_ID = UUID.fromString("70000000-0000-0000-0000-000000000003");
  private static final Instant OCORRIDO_EM = Instant.parse("2026-09-04T15:00:00Z");
  private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";

  @Mock private ItemAuditoriaRepository auditoriaRepository;

  private ItemAuditHistoryService service;

  @BeforeEach
  void setUp() {
    service = new ItemAuditHistoryService(auditoriaRepository, new ObjectMapper());
  }

  @Test
  @DisplayName("Deve retornar diff ordenado sem IDs nem chaves internas")
  void deveRetornarDiffSemIdentificadoresOuChavesInternas() {
    ItemAuditoria auditoria =
        novaAuditoria(
            ItemAuditoriaAcao.ALTERADO,
            """
            {
              "observacao":{"antes":"Antes","depois":"Depois"},
              "criadoPorId":{"antes":"70000000-0000-0000-0000-000000000004","depois":"70000000-0000-0000-0000-000000000005"},
              "arquivos":{"antes":[{"id":"70000000-0000-0000-0000-000000000006","nome":"antigo.pdf","chaveArmazenamento":"itens/antigo.pdf","origem":"ITEM"}],"depois":[{"id":"70000000-0000-0000-0000-000000000007","nome":"novo.pdf","chaveArmazenamento":"itens/novo.pdf","origem":"ITEM"}]}
            }
            """,
            null);
    when(auditoriaRepository.findByItemIdOrderByOcorridoEmDescIdDesc(ITEM_ID))
        .thenReturn(List.of(auditoria));

    ItemAuditoriaResponse response = service.listarPorItemId(ITEM_ID).getFirst();

    assertEquals("Auditor Teste", response.ator());
    assertEquals(OCORRIDO_EM, response.ocorridoEm());
    assertEquals(List.of("observacao", "arquivos"), List.copyOf(response.alteracoes().keySet()));
    assertEquals("Antes", response.alteracoes().get("observacao").antes().stringValue());
    assertEquals("Depois", response.alteracoes().get("observacao").depois().stringValue());
    JsonNode arquivoDepois = response.alteracoes().get("arquivos").depois().get(0);
    assertEquals("novo.pdf", arquivoDepois.get("nome").stringValue());
    assertEquals("ITEM", arquivoDepois.get("origem").stringValue());
    assertFalse(arquivoDepois.has("id"));
    assertFalse(arquivoDepois.has("chaveArmazenamento"));
    verify(auditoriaRepository).findByItemIdOrderByOcorridoEmDescIdDesc(ITEM_ID);
  }

  @Test
  @DisplayName("Deve converter snapshots de criacao e exclusao em antes e depois")
  void deveConverterSnapshotsDeCriacaoEExclusaoEmAntesEDepois() {
    String snapshot =
        """
        {
          "id":"70000000-0000-0000-0000-000000000002",
          "descricao":"Servico",
          "criadoPorId":"70000000-0000-0000-0000-000000000003",
          "arquivos":[{"id":"70000000-0000-0000-0000-000000000004","nome":"nota.pdf","chaveArmazenamento":"itens/nota.pdf","origem":"ITEM"}]
        }
        """;
    ItemAuditoria criada = novaAuditoria(ItemAuditoriaAcao.CRIADO, "{}", snapshot);
    ItemAuditoria excluida = novaAuditoria(ItemAuditoriaAcao.EXCLUIDO, "{}", snapshot);
    when(auditoriaRepository.findByItemIdOrderByOcorridoEmDescIdDesc(ITEM_ID))
        .thenReturn(List.of(excluida, criada));

    List<ItemAuditoriaResponse> responses = service.listarPorItemId(ITEM_ID);
    ItemAuditoriaAlteracaoResponse exclusaoDescricao =
        responses.getFirst().alteracoes().get("descricao");
    ItemAuditoriaAlteracaoResponse criacaoDescricao =
        responses.get(1).alteracoes().get("descricao");
    JsonNode arquivoCriado = responses.get(1).alteracoes().get("arquivos").depois().get(0);

    assertEquals("Servico", exclusaoDescricao.antes().stringValue());
    assertTrue(exclusaoDescricao.depois().isNull());
    assertTrue(criacaoDescricao.antes().isNull());
    assertEquals("Servico", criacaoDescricao.depois().stringValue());
    assertFalse(responses.getFirst().alteracoes().containsKey("id"));
    assertFalse(responses.getFirst().alteracoes().containsKey("criadoPorId"));
    assertFalse(arquivoCriado.has("id"));
    assertFalse(arquivoCriado.has("chaveArmazenamento"));
  }

  private ItemAuditoria novaAuditoria(
      ItemAuditoriaAcao acao, String alteracoes, String snapshotItem) {
    return new ItemAuditoria(
        ITEM_ID,
        acao,
        new ItemAuditoria.Ator(ATOR_ID, "Auditor Teste", "auditor@example.com"),
        new ItemAuditoria.ContextoRequisicao(
            OCORRIDO_EM, "203.0.113.10", "audit-test-agent", TRACE_ID),
        new ItemAuditoria.ConteudoEvento(alteracoes, snapshotItem));
  }
}
