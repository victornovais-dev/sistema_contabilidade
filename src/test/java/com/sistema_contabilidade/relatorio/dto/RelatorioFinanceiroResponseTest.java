package com.sistema_contabilidade.relatorio.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RelatorioFinanceiroResponse unit tests")
class RelatorioFinanceiroResponseTest {

  @Test
  @DisplayName("Deve copiar listas de receitas e despesas")
  void deveCopiarListasDeReceitasEDespesas() {
    RelatorioItemDto item =
        new RelatorioItemDto(
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            TipoItem.RECEITA,
            BigDecimal.TEN,
            LocalDate.of(2026, 3, 29),
            LocalDateTime.of(2026, 3, 29, 10, 0),
            "ALUGUEL");
    List<RelatorioItemDto> receitas = List.of(item);
    List<RelatorioItemDto> despesas = List.of();

    RelatorioFinanceiroResponse response =
        new RelatorioFinanceiroResponse(
            BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, receitas, despesas);

    assertEquals(1, response.receitas().size());
    assertEquals(0, response.despesas().size());
  }
}
