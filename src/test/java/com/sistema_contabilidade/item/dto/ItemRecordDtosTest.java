package com.sistema_contabilidade.item.dto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Item record DTOs unit tests")
class ItemRecordDtosTest {

  @Test
  @DisplayName("Deve expor campos de ItemCreateRequest")
  void deveExporCamposDeItemCreateRequest() {
    LocalDate data = LocalDate.of(2026, 3, 15);
    LocalDateTime horarioCriacao = LocalDateTime.of(2026, 3, 15, 18, 0, 0);
    byte[] arquivoPdf = "pdf".getBytes();
    ItemCreateRequest request =
        new ItemCreateRequest(
            new BigDecimal("99.90"), data, horarioCriacao, arquivoPdf, TipoItem.RECEITA);

    assertEquals(new BigDecimal("99.90"), request.valor());
    assertEquals(data, request.data());
    assertEquals(horarioCriacao, request.horarioCriacao());
    assertArrayEquals(arquivoPdf, request.arquivoPdf());
    assertEquals(TipoItem.RECEITA, request.tipo());
  }

  @Test
  @DisplayName("Deve expor campos de ItemUpdateRequest")
  void deveExporCamposDeItemUpdateRequest() {
    LocalDate data = LocalDate.of(2026, 3, 16);
    LocalDateTime horarioCriacao = LocalDateTime.of(2026, 3, 16, 9, 30, 0);
    byte[] arquivoPdf = "pdf-atualizado".getBytes();
    ItemUpdateRequest request =
        new ItemUpdateRequest(
            new BigDecimal("120.00"), data, horarioCriacao, arquivoPdf, TipoItem.DESPESA);

    assertEquals(new BigDecimal("120.00"), request.valor());
    assertEquals(data, request.data());
    assertEquals(horarioCriacao, request.horarioCriacao());
    assertArrayEquals(arquivoPdf, request.arquivoPdf());
    assertEquals(TipoItem.DESPESA, request.tipo());
  }

  @Test
  @DisplayName("Deve mapear Item para ItemResponse")
  void deveMapearItemParaItemResponse() {
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    LocalDate data = LocalDate.of(2026, 3, 17);
    LocalDateTime horarioCriacao = LocalDateTime.of(2026, 3, 17, 10, 15, 0);
    byte[] arquivoPdf = "pdf-item".getBytes();
    Item item = new Item();
    item.setId(id);
    item.setValor(new BigDecimal("45.20"));
    item.setData(data);
    item.setHorarioCriacao(horarioCriacao);
    item.setArquivoPdf(arquivoPdf);
    item.setTipo(TipoItem.RECEITA);

    ItemResponse response = ItemResponse.from(item);

    assertEquals(id, response.id());
    assertEquals(new BigDecimal("45.20"), response.valor());
    assertEquals(data, response.data());
    assertEquals(horarioCriacao, response.horarioCriacao());
    assertArrayEquals(arquivoPdf, response.arquivoPdf());
    assertEquals(TipoItem.RECEITA, response.tipo());
  }
}
