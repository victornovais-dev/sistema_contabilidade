package com.sistema_contabilidade.item.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sistema_contabilidade.item.dto.ItemListCursorDirection;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemListKeysetCursor;
import com.sistema_contabilidade.item.repository.ItemListPageQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("ItemListCursorService unit tests")
class ItemListCursorServiceTest {

  private static final String ACTIVE_SECRET = "0123456789ABCDEF0123456789ABCDEF";
  private static final String PREVIOUS_SECRET = "FEDCBA9876543210FEDCBA9876543210";
  private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

  @Test
  @DisplayName("Deve validar cursor vinculado ao escopo, filtros e tamanho de pagina")
  void deveValidarCursorVinculadoAoEscopoFiltrosETamanhoDePagina() {
    ItemListCursorService service = serviceAt(NOW, ACTIVE_SECRET, "");
    ItemListPageQuery query = query();
    ItemListKeysetCursor position = position();

    String cursor = service.create(query, 20, position);
    ItemListKeysetCursor parsed = service.parse(cursor, query, 20, ItemListCursorDirection.NEXT);

    assertEquals(position.horarioCriacao(), parsed.horarioCriacao());
    assertEquals(position.id(), parsed.id());
    assertEquals(ItemListCursorDirection.NEXT, parsed.direction());
  }

  @Test
  @DisplayName("Deve rejeitar cursor adulterado ou reutilizado com filtro diferente")
  void deveRejeitarCursorAdulteradoOuReutilizadoComFiltroDiferente() {
    ItemListCursorService service = serviceAt(NOW, ACTIVE_SECRET, "");
    String cursor = service.create(query(), 20, position());
    String adulterado = cursor.substring(0, cursor.length() - 1) + "A";
    ItemListPageQuery outroFiltro =
        new ItemListPageQuery(
            Set.of("FINANCEIRO"),
            TipoItem.DESPESA,
            LocalDate.of(2026, Month.JULY, 1),
            null,
            "OUTROS",
            "alpha");

    assertBadRequest(() -> service.parse(adulterado, query(), 20, ItemListCursorDirection.NEXT));
    assertBadRequest(() -> service.parse(cursor, outroFiltro, 20, ItemListCursorDirection.NEXT));
    assertBadRequest(() -> service.parse(cursor, query(), 10, ItemListCursorDirection.NEXT));
  }

  @Test
  @DisplayName("Deve rejeitar cursor expirado apos dez minutos")
  void deveRejeitarCursorExpiradoAposDezMinutos() {
    ItemListCursorService issuingService = serviceAt(NOW, ACTIVE_SECRET, "");
    String cursor = issuingService.create(query(), 20, position());
    ItemListCursorService expiredService = serviceAt(NOW.plusSeconds(601), ACTIVE_SECRET, "");

    assertBadRequest(() -> expiredService.parse(cursor, query(), 20, ItemListCursorDirection.NEXT));
  }

  @Test
  @DisplayName("Deve aceitar cursor assinado pela chave anterior durante rotacao")
  void deveAceitarCursorAssinadoPelaChaveAnteriorDuranteRotacao() {
    String cursor = serviceAt(NOW, PREVIOUS_SECRET, "").create(query(), 20, position());
    ItemListCursorService rotatingService = serviceAt(NOW, ACTIVE_SECRET, PREVIOUS_SECRET);

    ItemListKeysetCursor parsed =
        rotatingService.parse(cursor, query(), 20, ItemListCursorDirection.PREVIOUS);

    assertEquals(ItemListCursorDirection.PREVIOUS, parsed.direction());
  }

  private ItemListCursorService serviceAt(Instant now, String activeSecret, String previousSecret) {
    return new ItemListCursorService(
        activeSecret, previousSecret, Clock.fixed(now, ZoneOffset.UTC));
  }

  private ItemListPageQuery query() {
    return new ItemListPageQuery(
        Set.of("OPERADOR", "FINANCEIRO"),
        TipoItem.DESPESA,
        LocalDate.of(2026, Month.JULY, 1),
        LocalDate.of(2026, Month.JULY, 31),
        "SERVICOS",
        "fornecedor alpha");
  }

  private ItemListKeysetCursor position() {
    return new ItemListKeysetCursor(
        LocalDateTime.of(2026, Month.JULY, 30, 10, 15),
        UUID.fromString("11111111-2222-3333-4444-555555555555"),
        ItemListCursorDirection.NEXT);
  }

  private void assertBadRequest(org.junit.jupiter.api.function.Executable executable) {
    ResponseStatusException exception = assertThrows(ResponseStatusException.class, executable);
    assertEquals(400, exception.getStatusCode().value());
  }
}
