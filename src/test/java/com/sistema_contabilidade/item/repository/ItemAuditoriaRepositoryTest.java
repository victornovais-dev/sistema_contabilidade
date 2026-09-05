package com.sistema_contabilidade.item.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.database.crypto.service.BlindIndexService;
import com.sistema_contabilidade.database.crypto.service.DatabaseCryptoService;
import com.sistema_contabilidade.item.model.ItemAuditoria;
import com.sistema_contabilidade.item.model.ItemAuditoriaAcao;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({DatabaseCryptoService.class, BlindIndexService.class})
@DisplayName("ItemAuditoriaRepository DataJpa tests")
class ItemAuditoriaRepositoryTest {

  private static final UUID ITEM_ID = UUID.fromString("70000000-0000-0000-0000-000000000002");
  private static final UUID ATOR_ID = UUID.fromString("70000000-0000-0000-0000-000000000003");
  private static final Instant OCORRIDO_EM = Instant.parse("2026-09-04T15:00:00Z");
  private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";

  @Autowired private ItemAuditoriaRepository auditoriaRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("Deve cifrar campos sensiveis e listar eventos mais recentes primeiro")
  void deveCifrarCamposSensiveisEListarEventosMaisRecentesPrimeiro() {
    ItemAuditoria criacao =
        novaAuditoria(
            ItemAuditoriaAcao.CRIADO,
            OCORRIDO_EM,
            "{\"valor\":{\"antes\":null,\"depois\":100}}",
            "{\"id\":\"item-1\",\"arquivo\":{\"nome\":\"nota.pdf\",\"chave\":\"itens/nota.pdf\"}}");
    ItemAuditoria alteracao =
        novaAuditoria(
            ItemAuditoriaAcao.ALTERADO,
            OCORRIDO_EM.plus(1, ChronoUnit.MINUTES),
            "{\"valor\":{\"antes\":100,\"depois\":120}}",
            null);

    auditoriaRepository.save(criacao);
    auditoriaRepository.save(alteracao);
    entityManager.flush();

    Map<String, Object> raw =
        jdbcTemplate.queryForMap(
            """
            select ator_nome, ator_email, ip_origem, alteracoes, snapshot_item,
                   user_agent, trace_id
            from item_auditorias
            where id = ?
            """,
            criacao.getId());

    assertEncrypted(raw, "ator_nome", "Auditor Teste");
    assertEncrypted(raw, "ator_email", "auditor@example.com");
    assertEncrypted(raw, "ip_origem", "203.0.113.10");
    assertEncrypted(raw, "alteracoes", criacao.getAlteracoes());
    assertEncrypted(raw, "snapshot_item", criacao.getSnapshotItem());
    assertEquals("audit-test-agent", raw.get("user_agent"));
    assertEquals(TRACE_ID, raw.get("trace_id"));

    entityManager.clear();
    List<ItemAuditoria> eventos =
        auditoriaRepository.findByItemIdOrderByOcorridoEmDescIdDesc(ITEM_ID);

    assertEquals(2, eventos.size());
    assertEquals(ItemAuditoriaAcao.ALTERADO, eventos.getFirst().getAcao());
    assertEquals("Auditor Teste", eventos.getFirst().getAtorNome());
    assertEquals("auditor@example.com", eventos.getFirst().getAtorEmail());
    assertEquals("203.0.113.10", eventos.getFirst().getIpOrigem());
    assertNull(eventos.getFirst().getSnapshotItem());
    assertEquals(ItemAuditoriaAcao.CRIADO, eventos.getLast().getAcao());
  }

  @Test
  @DisplayName("Deve manter entidade imutavel e repositorio sem operacoes de exclusao")
  void deveManterEntidadeImutavelERepositorioSemOperacoesDeExclusao() throws Exception {
    ItemAuditoria auditoria =
        novaAuditoria(
            ItemAuditoriaAcao.OBSERVACAO_ALTERADA,
            OCORRIDO_EM,
            "{\"observacao\":{\"antes\":\"A\",\"depois\":\"B\"}}",
            null);
    auditoriaRepository.save(auditoria);
    entityManager.flush();
    entityManager.clear();

    ItemAuditoria persistida =
        auditoriaRepository.findByItemIdOrderByOcorridoEmDescIdDesc(ITEM_ID).getFirst();
    Field alteracoes = ItemAuditoria.class.getDeclaredField("alteracoes");
    alteracoes.setAccessible(true);
    alteracoes.set(persistida, "conteudo-adulterado");
    entityManager.flush();
    entityManager.clear();

    ItemAuditoria recarregada =
        auditoriaRepository.findByItemIdOrderByOcorridoEmDescIdDesc(ITEM_ID).getFirst();
    assertEquals(
        "{\"observacao\":{\"antes\":\"A\",\"depois\":\"B\"}}", recarregada.getAlteracoes());
    assertTrue(ItemAuditoria.class.isAnnotationPresent(Immutable.class));
    assertFalse(
        Arrays.stream(ItemAuditoriaRepository.class.getMethods())
            .map(java.lang.reflect.Method::getName)
            .anyMatch(nome -> nome.startsWith("delete")));
  }

  private ItemAuditoria novaAuditoria(
      ItemAuditoriaAcao acao, Instant ocorridoEm, String alteracoes, String snapshotItem) {
    return new ItemAuditoria(
        ITEM_ID,
        acao,
        new ItemAuditoria.Ator(ATOR_ID, "Auditor Teste", "auditor@example.com"),
        new ItemAuditoria.ContextoRequisicao(
            ocorridoEm, "203.0.113.10", "audit-test-agent", TRACE_ID),
        new ItemAuditoria.ConteudoEvento(alteracoes, snapshotItem));
  }

  private void assertEncrypted(Map<String, Object> raw, String column, String clearValue) {
    String storedValue = raw.get(column).toString();
    assertTrue(storedValue.startsWith("enc:v1:"));
    assertNotEquals(clearValue, storedValue);
  }
}
