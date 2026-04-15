package com.sistema_contabilidade.item.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.item.model.ItemDescricao;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemDescricaoRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("ItemDescricaoInitializer unit tests")
class ItemDescricaoInitializerTest {

  @Test
  @DisplayName("Deve criar e atualizar descricoes padrao no banco")
  void deveCriarEAtualizarDescricoesPadraoNoBanco() {
    ItemDescricaoRepository itemDescricaoRepository =
        org.mockito.Mockito.mock(ItemDescricaoRepository.class);
    when(itemDescricaoRepository.findByTipoAndNomeIgnoreCase(any(), any()))
        .thenReturn(Optional.empty());

    ItemDescricaoInitializer initializer = new ItemDescricaoInitializer(itemDescricaoRepository);

    initializer.run();

    ArgumentCaptor<ItemDescricao> captor = ArgumentCaptor.forClass(ItemDescricao.class);
    verify(itemDescricaoRepository, times(ItemDescricaoCatalog.defaultDescriptions().size()))
        .save(captor.capture());
    ItemDescricao ultimaDescricao = captor.getAllValues().getLast();
    assertEquals(TipoItem.DESPESA, ultimaDescricao.getTipo());
    assertEquals("Outras despesas", ultimaDescricao.getNome());
    assertEquals(9999, ultimaDescricao.getOrdem());
  }
}
