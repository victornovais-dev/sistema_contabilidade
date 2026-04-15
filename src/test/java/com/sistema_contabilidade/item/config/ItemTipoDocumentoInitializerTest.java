package com.sistema_contabilidade.item.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.item.model.ItemTipoDocumento;
import com.sistema_contabilidade.item.repository.ItemTipoDocumentoRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("ItemTipoDocumentoInitializer unit tests")
class ItemTipoDocumentoInitializerTest {

  @Test
  @DisplayName("Deve criar e atualizar tipos de documento padrao no banco")
  void deveCriarEAtualizarTiposDocumentoPadraoNoBanco() {
    ItemTipoDocumentoRepository itemTipoDocumentoRepository =
        org.mockito.Mockito.mock(ItemTipoDocumentoRepository.class);
    when(itemTipoDocumentoRepository.findByNomeIgnoreCase(any())).thenReturn(Optional.empty());

    ItemTipoDocumentoInitializer initializer =
        new ItemTipoDocumentoInitializer(itemTipoDocumentoRepository);

    initializer.run();

    ArgumentCaptor<ItemTipoDocumento> captor = ArgumentCaptor.forClass(ItemTipoDocumento.class);
    verify(
            itemTipoDocumentoRepository,
            times(ItemTipoDocumentoCatalog.defaultDocumentTypes().size()))
        .save(captor.capture());
    ItemTipoDocumento ultimoTipoDocumento = captor.getAllValues().getLast();
    assertEquals("Outros", ultimoTipoDocumento.getNome());
    assertEquals(40, ultimoTipoDocumento.getOrdem());
  }
}
