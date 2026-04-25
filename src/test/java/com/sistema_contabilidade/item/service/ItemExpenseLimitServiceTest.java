package com.sistema_contabilidade.item.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.item.dto.ItemUpsertRequest;
import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("ItemExpenseLimitService unit tests")
class ItemExpenseLimitServiceTest {

  @Test
  @DisplayName("Deve ignorar categorias sem limite especial")
  void deveIgnorarCategoriasSemLimiteEspecial() {
    ItemRepository itemRepository = Mockito.mock(ItemRepository.class);
    ItemExpenseLimitService service = new ItemExpenseLimitService(itemRepository);

    assertDoesNotThrow(
        () -> service.validarLimiteDespesa(request("INTERNET", "50.00"), "ADMIN", null));
  }

  @Test
  @DisplayName("Deve bloquear combustivel acima de 10 por cento das despesas")
  void deveBloquearCombustivelAcimaDeDezPorCentoDasDespesas() {
    ItemRepository itemRepository = Mockito.mock(ItemRepository.class);
    ItemExpenseLimitService service = new ItemExpenseLimitService(itemRepository);

    when(itemRepository.findByTipoAndRoleNome(TipoItem.DESPESA, "ADMIN"))
        .thenReturn(List.of(item("INTERNET", "900.00", null)));

    ItemUpsertRequest request = request("Combustíveis e lubrificantes", "200.00");
    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> service.validarLimiteDespesa(request, "ADMIN", null));

    assertEquals(400, exception.getStatusCode().value());
    assertEquals(
        "Nao e permitido adicionar esta despesa. Combustivel e lubrificantes pode representar no maximo 10% do total de despesas.",
        exception.getReason());
  }

  @Test
  @DisplayName("Deve bloquear locacao somando aluguel de imoveis e veiculos")
  void deveBloquearLocacaoSomandoAluguelDeImoveisEVeiculos() {
    ItemRepository itemRepository = Mockito.mock(ItemRepository.class);
    ItemExpenseLimitService service = new ItemExpenseLimitService(itemRepository);

    when(itemRepository.findByTipoAndRoleNome(TipoItem.DESPESA, "ADMIN"))
        .thenReturn(
            List.of(item("Aluguel de imóveis", "150.00", null), item("Internet", "350.00", null)));

    ItemUpsertRequest request = request("Aluguel de veículos", "10.00");
    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> service.validarLimiteDespesa(request, "ADMIN", null));

    assertEquals(
        "Nao e permitido adicionar esta despesa. Locacao pode representar no maximo 20% do total de despesas.",
        exception.getReason());
  }

  @Test
  @DisplayName("Deve permitir locacao no limite de 20 por cento")
  void devePermitirLocacaoNoLimiteDeVintePorCento() {
    ItemRepository itemRepository = Mockito.mock(ItemRepository.class);
    ItemExpenseLimitService service = new ItemExpenseLimitService(itemRepository);

    when(itemRepository.findByTipoAndRoleNome(TipoItem.DESPESA, "ADMIN"))
        .thenReturn(List.of(item("Internet", "800.00", null)));

    assertDoesNotThrow(
        () -> service.validarLimiteDespesa(request("Aluguel de imóveis", "200.00"), "ADMIN", null));
  }

  @Test
  @DisplayName("Deve ignorar o proprio item ao validar atualizacao")
  void deveIgnorarOProprioItemAoValidarAtualizacao() {
    ItemRepository itemRepository = Mockito.mock(ItemRepository.class);
    ItemExpenseLimitService service = new ItemExpenseLimitService(itemRepository);
    UUID itemId = UUID.fromString("aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb");

    when(itemRepository.findByTipoAndRoleNomeAndIdNot(TipoItem.DESPESA, "ADMIN", itemId))
        .thenReturn(List.of(item("Internet", "900.00", UUID.randomUUID())));

    assertDoesNotThrow(
        () ->
            service.validarLimiteDespesa(
                request("Combustíveis e lubrificantes", "100.00"), "ADMIN", itemId));

    verify(itemRepository).findByTipoAndRoleNomeAndIdNot(TipoItem.DESPESA, "ADMIN", itemId);
  }

  private ItemUpsertRequest request(String descricao, String valor) {
    return new ItemUpsertRequest(
        new BigDecimal(valor),
        LocalDate.of(2026, 4, 15),
        LocalDateTime.of(2026, 4, 15, 10, 0),
        List.of(),
        List.of(),
        TipoItem.DESPESA,
        "ADMIN",
        descricao,
        null,
        null,
        null,
        null,
        null);
  }

  private Item item(String descricao, String valor, UUID id) {
    Item item = new Item();
    item.setId(id);
    item.setTipo(TipoItem.DESPESA);
    item.setRoleNome("ADMIN");
    item.setDescricao(descricao);
    item.setValor(new BigDecimal(valor));
    return item;
  }
}
