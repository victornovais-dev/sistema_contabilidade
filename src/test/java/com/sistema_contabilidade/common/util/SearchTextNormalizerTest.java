package com.sistema_contabilidade.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SearchTextNormalizer unit tests")
class SearchTextNormalizerTest {

  @Test
  @DisplayName("Deve normalizar acentos, pontuacao e espacos para busca")
  void deveNormalizarAcentosPontuacaoEEspacosParaBusca() {
    assertEquals(
        "FORNECEDOR 100 ACAO LTDA",
        SearchTextNormalizer.normalizeForSearch("  Fornecedor_100% Ação Ltda.  "));
  }

  @Test
  @DisplayName("Deve tokenizar termos distintos na ordem original")
  void deveTokenizarTermosDistintosNaOrdemOriginal() {
    assertIterableEquals(
        List.of("ALPHA", "BETA"), SearchTextNormalizer.tokenize(" alpha beta alpha "));
  }

  @Test
  @DisplayName("Deve montar query booleana por prefixo quando todos os tokens sao elegiveis")
  void deveMontarQueryBooleanaPorPrefixoQuandoTodosOsTokensSaoElegiveis() {
    assertTrue(SearchTextNormalizer.allTokensHaveMinLength("fornecedor alpha", 3));
    assertEquals(
        "+FORNECEDOR* +ALPHA*", SearchTextNormalizer.toBooleanPrefixQuery("fornecedor alpha", 3));
  }

  @Test
  @DisplayName("Nao deve montar query booleana quando os tokens sao curtos demais")
  void naoDeveMontarQueryBooleanaQuandoOsTokensSaoCurtosDemais() {
    assertFalse(SearchTextNormalizer.allTokensHaveMinLength("ab cd", 3));
    assertNull(SearchTextNormalizer.toBooleanPrefixQuery("ab cd", 3));
  }
}
