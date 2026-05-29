package com.sistema_contabilidade.relatorio.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RelatorioItemDto unit tests")
class RelatorioItemDtoTest {

  @Test
  @DisplayName("Deve criar DTO a partir do item")
  void deveCriarDtoAPartirDoItem() {
    Item item = new Item();
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    LocalDate data = LocalDate.of(2026, 5, 20);
    LocalDateTime horarioCriacao = LocalDateTime.of(2026, 5, 20, 14, 30);
    item.setId(id);
    item.setTipo(TipoItem.DESPESA);
    item.setValor(new BigDecimal("125.40"));
    item.setData(data);
    item.setHorarioCriacao(horarioCriacao);
    item.setDescricao("SERVICOS");

    RelatorioItemDto dto = RelatorioItemDto.from(item);

    assertEquals(id, dto.id());
    assertEquals(TipoItem.DESPESA, dto.tipo());
    assertEquals(new BigDecimal("125.40"), dto.valor());
    assertEquals(data, dto.data());
    assertEquals(horarioCriacao, dto.horarioCriacao());
    assertEquals("SERVICOS", dto.descricao());
  }
}
