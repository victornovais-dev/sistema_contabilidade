package com.sistema_contabilidade.item.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.item.model.ItemTipoDocumento;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemTipoDocumentoRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ItemTipoDocumentoService unit tests")
class ItemTipoDocumentoServiceTest {

  @Test
  @DisplayName("Deve listar tipos de documento vindos do banco")
  void deveListarTiposDocumentoVindosDoBanco() {
    ItemTipoDocumentoRepository repository = mock(ItemTipoDocumentoRepository.class);
    when(repository.findAllByOrderByOrdemAscNomeAsc())
        .thenReturn(
            List.of(
                tipoDocumento("Nota fiscal", 10),
                tipoDocumento("Fatura", 20),
                tipoDocumento("Boleto", 30),
                tipoDocumento("Outros", 40)));

    ItemTipoDocumentoService service = new ItemTipoDocumentoService(repository);

    assertEquals(
        List.of("Nota fiscal", "Fatura", "Boleto", "Outros"), service.listarTiposDocumento());
  }

  @Test
  @DisplayName("Deve usar catalogo padrao quando banco vier vazio")
  void deveUsarCatalogoPadraoQuandoBancoVierVazio() {
    ItemTipoDocumentoRepository repository = mock(ItemTipoDocumentoRepository.class);
    when(repository.findAllByOrderByOrdemAscNomeAsc()).thenReturn(List.of());

    ItemTipoDocumentoService service = new ItemTipoDocumentoService(repository);

    assertEquals(
        List.of("Nota fiscal", "Fatura", "Boleto", "Outros"), service.listarTiposDocumento());
  }

  @Test
  @DisplayName("Deve usar catalogo padrao quando consulta ao banco falhar")
  void deveUsarCatalogoPadraoQuandoConsultaAoBancoFalhar() {
    ItemTipoDocumentoRepository repository = mock(ItemTipoDocumentoRepository.class);
    when(repository.findAllByOrderByOrdemAscNomeAsc())
        .thenThrow(new IllegalStateException("erro de banco"));

    ItemTipoDocumentoService service = new ItemTipoDocumentoService(repository);

    assertEquals(
        List.of("Nota fiscal", "Fatura", "Boleto", "Outros"), service.listarTiposDocumento());
  }

  @Test
  @DisplayName("Deve listar tipos de documento de receita")
  void deveListarTiposDocumentoDeReceita() {
    ItemTipoDocumentoRepository repository = mock(ItemTipoDocumentoRepository.class);
    ItemTipoDocumentoService service = new ItemTipoDocumentoService(repository);

    assertEquals(
        List.of("Pix", "Transferência", "Cheque", "Dinheiro"),
        service.listarTiposDocumentoPorTipo(TipoItem.RECEITA));
  }

  @Test
  @DisplayName("Deve listar tipos de documento de despesa")
  void deveListarTiposDocumentoDeDespesa() {
    ItemTipoDocumentoRepository repository = mock(ItemTipoDocumentoRepository.class);
    ItemTipoDocumentoService service = new ItemTipoDocumentoService(repository);

    assertEquals(
        List.of("Nota fiscal", "Fatura", "Boleto", "Outros"),
        service.listarTiposDocumentoPorTipo(TipoItem.DESPESA));
  }

  private ItemTipoDocumento tipoDocumento(String nome, int ordem) {
    ItemTipoDocumento itemTipoDocumento = new ItemTipoDocumento();
    itemTipoDocumento.setNome(nome);
    itemTipoDocumento.setOrdem(ordem);
    return itemTipoDocumento;
  }
}
