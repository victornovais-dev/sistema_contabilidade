package com.sistema_contabilidade.item.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ItemArquivo model tests")
class ItemArquivoTest {

  @Test
  @DisplayName("Deve permitir setar e recuperar campos de ItemArquivo")
  void devePermitirSetarERecuperarCamposDeItemArquivo() {
    Item item = new Item();
    ItemArquivo arquivo = new ItemArquivo();
    UUID id = UUID.fromString("99999999-9999-9999-9999-999999999999");

    arquivo.setId(id);
    arquivo.setCaminhoArquivoPdf("uploads/itens/arquivo.pdf");
    arquivo.setItem(item);

    assertEquals(id, arquivo.getId());
    assertEquals("uploads/itens/arquivo.pdf", arquivo.getCaminhoArquivoPdf());
    assertEquals(item, arquivo.getItem());
  }
}
