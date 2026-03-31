package com.sistema_contabilidade.item.dto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ItemRequestArraySupport unit tests")
class ItemRequestArraySupportTest {

  @Test
  @DisplayName("Deve fazer copia defensiva dos arrays")
  void deveFazerCopiaDefensivaDosArrays() {
    byte[] original = new byte[] {1, 2, 3};
    List<byte[]> copia = ItemRequestArraySupport.copyOf(List.of(original));

    original[0] = 9;

    assertArrayEquals(new byte[] {1, 2, 3}, copia.get(0));
    assertNotEquals(original, copia.get(0));
  }

  @Test
  @DisplayName("Deve comparar listas de arrays por conteudo")
  void deveCompararListasDeArraysPorConteudo() {
    List<byte[]> listaA = List.of(new byte[] {1, 2}, new byte[] {3, 4});
    List<byte[]> listaB = List.of(new byte[] {1, 2}, new byte[] {3, 4});
    List<byte[]> listaC = List.of(new byte[] {1, 9});

    assertTrue(ItemRequestArraySupport.listsEqual(listaA, listaB));
    assertFalse(ItemRequestArraySupport.listsEqual(listaA, listaC));
    assertFalse(ItemRequestArraySupport.listsEqual(listaA, null));
  }

  @Test
  @DisplayName("Deve calcular hash e string das listas")
  void deveCalcularHashEStringDasListas() {
    List<byte[]> lista = List.of(new byte[] {1, 2, 3});

    int hash = ItemRequestArraySupport.listHashCode(lista);
    String texto = ItemRequestArraySupport.listToString(lista);

    assertEquals(hash, ItemRequestArraySupport.listHashCode(lista));
    assertTrue(texto.contains("[1, 2, 3]"));
  }
}
