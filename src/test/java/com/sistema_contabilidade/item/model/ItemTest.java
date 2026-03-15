package com.sistema_contabilidade.item.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
    byte[] arquivoPdf = "pdf".getBytes();

    item.setValor(valor);
    item.setData(data);
    item.setHorarioCriacao(horarioCriacao);
    item.setArquivoPdf(arquivoPdf);
    item.setTipo(TipoItem.RECEITA);

    assertEquals(valor, item.getValor());
    assertEquals(data, item.getData());
    assertEquals(horarioCriacao, item.getHorarioCriacao());
    assertArrayEquals(arquivoPdf, item.getArquivoPdf());
    assertEquals(TipoItem.RECEITA, item.getTipo());
  }
}
