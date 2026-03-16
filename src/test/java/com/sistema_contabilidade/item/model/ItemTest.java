package com.sistema_contabilidade.item.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Item model unit tests")
class ItemTest {

  @Test
  @DisplayName("Deve armazenar dados de item com tipo receita")
  void deveArmazenarDadosDeItemComTipoReceita() {
    Item item = new Item();
    BigDecimal valor = new BigDecimal("123.45");
    LocalDate data = LocalDate.of(2026, 3, 15);
    LocalDateTime horarioCriacao = LocalDateTime.of(2026, 3, 15, 18, 0, 0);
    String caminhoArquivoPdf = "uploads/itens/item-123.pdf";

    item.setValor(valor);
    item.setData(data);
    item.setHorarioCriacao(horarioCriacao);
    item.setCaminhoArquivoPdf(caminhoArquivoPdf);
    item.setTipo(TipoItem.RECEITA);

    assertEquals(valor, item.getValor());
    assertEquals(data, item.getData());
    assertEquals(horarioCriacao, item.getHorarioCriacao());
    assertEquals(caminhoArquivoPdf, item.getCaminhoArquivoPdf());
    assertEquals(TipoItem.RECEITA, item.getTipo());
  }
}
