package com.sistema_contabilidade.item.dto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
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
    String caminhoArquivoPdf = "uploads/itens/item.pdf";
    Item item = new Item();
    item.setId(id);
    item.setValor(new BigDecimal("45.20"));
    item.setData(data);
    item.setHorarioCriacao(horarioCriacao);
    item.setCaminhoArquivoPdf(caminhoArquivoPdf);
    item.setTipo(TipoItem.RECEITA);

    ItemResponse response = ItemResponse.from(item);

    assertEquals(id, response.id());
    assertEquals(new BigDecimal("45.20"), response.valor());
    assertEquals(data, response.data());
    assertEquals(horarioCriacao, response.horarioCriacao());
    assertEquals(caminhoArquivoPdf, response.caminhoArquivoPdf());
    assertEquals(TipoItem.RECEITA, response.tipo());
  }

  @Test
  @DisplayName("Deve comparar ItemCreateRequest por conteudo do array")
  void deveCompararItemCreateRequestPorConteudoDoArray() {
    LocalDate data = LocalDate.of(2026, 3, 15);
    LocalDateTime horarioCriacao = LocalDateTime.of(2026, 3, 15, 18, 0, 0);
    ItemCreateRequest requestA =
        new ItemCreateRequest(
            new BigDecimal("99.90"), data, horarioCriacao, new byte[] {1, 2, 3}, TipoItem.RECEITA);
    ItemCreateRequest requestB =
        new ItemCreateRequest(
            new BigDecimal("99.90"), data, horarioCriacao, new byte[] {1, 2, 3}, TipoItem.RECEITA);
    ItemCreateRequest requestDiferente =
        new ItemCreateRequest(
            new BigDecimal("99.90"), data, horarioCriacao, new byte[] {9, 9, 9}, TipoItem.RECEITA);

    assertEquals(requestA, Objects.requireNonNull(requestA));
    assertEquals(requestA, requestB);
    assertEquals(requestA.hashCode(), requestB.hashCode());
    assertNotEquals(requestA, requestDiferente);
    boolean equalsNull = requestA.equals(null);
    boolean equalsOutroTipo = requestA.equals("tipo-invalido");
    assertNotEquals(true, equalsNull);
    assertNotEquals(true, equalsOutroTipo);
    assertNotNull(requestA.toString());
    assertTrue(requestA.toString().contains("arquivoPdf=[1, 2, 3]"));
  }

  @Test
  @DisplayName("Deve comparar ItemUpdateRequest por conteudo do array")
  void deveCompararItemUpdateRequestPorConteudoDoArray() {
    LocalDate data = LocalDate.of(2026, 3, 16);
    LocalDateTime horarioCriacao = LocalDateTime.of(2026, 3, 16, 9, 30, 0);
    ItemUpdateRequest requestA =
        new ItemUpdateRequest(
            new BigDecimal("120.00"), data, horarioCriacao, new byte[] {4, 5, 6}, TipoItem.DESPESA);
    ItemUpdateRequest requestB =
        new ItemUpdateRequest(
            new BigDecimal("120.00"), data, horarioCriacao, new byte[] {4, 5, 6}, TipoItem.DESPESA);
    ItemUpdateRequest requestDiferente =
        new ItemUpdateRequest(
            new BigDecimal("120.00"), data, horarioCriacao, new byte[] {9, 9, 9}, TipoItem.DESPESA);

    assertEquals(requestA, Objects.requireNonNull(requestA));
    assertEquals(requestA, requestB);
    assertEquals(requestA.hashCode(), requestB.hashCode());
    assertNotEquals(requestA, requestDiferente);
    boolean equalsNull = requestA.equals(null);
    boolean equalsOutroTipo = requestA.equals("tipo-invalido");
    assertNotEquals(true, equalsNull);
    assertNotEquals(true, equalsOutroTipo);
    assertNotNull(requestA.toString());
    assertTrue(requestA.toString().contains("arquivoPdf=[4, 5, 6]"));
  }

  @Test
  @DisplayName("Deve fazer copia defensiva do array em ItemCreateRequest")
  void deveFazerCopiaDefensivaDoArrayEmItemCreateRequest() {
    byte[] arquivoPdf = new byte[] {1, 2, 3};
    ItemCreateRequest request =
        new ItemCreateRequest(
            new BigDecimal("10.00"),
            LocalDate.of(2026, 3, 16),
            LocalDateTime.of(2026, 3, 16, 10, 0),
            arquivoPdf,
            TipoItem.RECEITA);

    arquivoPdf[0] = 9;
    byte[] retorno = request.arquivoPdf();
    retorno[1] = 9;

    assertArrayEquals(new byte[] {1, 2, 3}, request.arquivoPdf());
  }

  @Test
  @DisplayName("Deve fazer copia defensiva do array em ItemUpdateRequest")
  void deveFazerCopiaDefensivaDoArrayEmItemUpdateRequest() {
    byte[] arquivoPdf = new byte[] {4, 5, 6};
    ItemUpdateRequest request =
        new ItemUpdateRequest(
            new BigDecimal("10.00"),
            LocalDate.of(2026, 3, 16),
            LocalDateTime.of(2026, 3, 16, 10, 0),
            arquivoPdf,
            TipoItem.DESPESA);

    arquivoPdf[0] = 9;
    byte[] retorno = request.arquivoPdf();
    retorno[1] = 9;

    assertArrayEquals(new byte[] {4, 5, 6}, request.arquivoPdf());
  }
}
