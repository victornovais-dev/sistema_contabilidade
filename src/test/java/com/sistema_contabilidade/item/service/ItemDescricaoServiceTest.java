package com.sistema_contabilidade.item.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.item.model.ItemDescricao;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemDescricaoRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ItemDescricaoService unit tests")
class ItemDescricaoServiceTest {

  @Test
  @DisplayName("Deve ordenar despesas alfabeticamente e manter outras despesas por ultimo")
  void deveOrdenarDespesasAlfabeticamenteMantendoOutrasDespesasPorUltimo() {
    ItemDescricaoRepository repository = mock(ItemDescricaoRepository.class);
    when(repository.findByTipoOrderByOrdemAscNomeAsc(TipoItem.DESPESA))
        .thenReturn(
            List.of(
                descricao(TipoItem.DESPESA, "Taxas bancárias", 290),
                descricao(TipoItem.DESPESA, "Outras despesas", 9999),
                descricao(TipoItem.DESPESA, "Água", 150),
                descricao(TipoItem.DESPESA, "Alimentação", 200)));

    ItemDescricaoService service = new ItemDescricaoService(repository);

    List<String> descricoes = service.listarDescricoesPorTipo(TipoItem.DESPESA);

    assertEquals(List.of("Água", "Alimentação", "Taxas bancárias", "Outras despesas"), descricoes);
  }

  @Test
  @DisplayName("Deve ordenar receitas alfabeticamente")
  void deveOrdenarReceitasAlfabeticamente() {
    ItemDescricaoRepository repository = mock(ItemDescricaoRepository.class);
    when(repository.findByTipoOrderByOrdemAscNomeAsc(TipoItem.RECEITA))
        .thenReturn(
            List.of(
                descricao(TipoItem.RECEITA, "ESTIMÁVEL", 40),
                descricao(TipoItem.RECEITA, "CONTA FP", 20),
                descricao(TipoItem.RECEITA, "CONTA DC", 30),
                descricao(TipoItem.RECEITA, "CONTA FEFEC", 15),
                descricao(TipoItem.RECEITA, "CONTA FEFC", 10)));

    ItemDescricaoService service = new ItemDescricaoService(repository);

    List<String> descricoes = service.listarDescricoesPorTipo(TipoItem.RECEITA);

    assertEquals(List.of("CONTA DC", "CONTA FEFC", "CONTA FP", "ESTIMÁVEL"), descricoes);
  }

  private ItemDescricao descricao(TipoItem tipo, String nome, int ordem) {
    ItemDescricao itemDescricao = new ItemDescricao();
    itemDescricao.setTipo(tipo);
    itemDescricao.setNome(nome);
    itemDescricao.setOrdem(ordem);
    return itemDescricao;
  }
}
