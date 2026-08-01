package com.sistema_contabilidade.item.dto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.item.model.ContaOrigemPagamentoItem;
import com.sistema_contabilidade.item.model.FormaPagamentoItem;
import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.ItemParcelaPagamento;
import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Item record DTOs unit tests")
class ItemRecordDtosTest {

  @Test
  @DisplayName("Deve expor campos de ItemUpsertRequest")
  void deveExporCamposDeItemUpsertRequest() {
    LocalDate data = LocalDate.of(2026, Month.MARCH, 15);
    LocalDateTime horarioCriacao = LocalDateTime.of(2026, Month.MARCH, 15, 18, 0, 0);
    byte[] arquivoPdf = "pdf".getBytes();
    byte[] arquivoPdf2 = "pdf2".getBytes();
    List<String> nomesArquivos = List.of("documento-1.pdf", "documento-2.pdf");
    ItemUpsertRequest request =
        new ItemUpsertRequest(
            new BigDecimal("99.90"),
            data,
            horarioCriacao,
            List.of(arquivoPdf, arquivoPdf2),
            nomesArquivos,
            TipoItem.RECEITA,
            "FINANCEIRO",
            "ALUGUEL",
            "Nota fiscal",
            "NF-12345",
            "EMPRESA TESTE",
            "123.456.789-00",
            "Observacao");

    assertEquals(new BigDecimal("99.90"), request.valor());
    assertEquals(data, request.data());
    assertEquals(horarioCriacao, request.horarioCriacao());
    assertEquals(2, request.arquivosPdf().size());
    assertArrayEquals(arquivoPdf, request.arquivosPdf().get(0));
    assertArrayEquals(arquivoPdf2, request.arquivosPdf().get(1));
    assertEquals(nomesArquivos, request.nomesArquivos());
    assertEquals(TipoItem.RECEITA, request.tipo());
    assertEquals("FINANCEIRO", request.role());
    assertEquals("ALUGUEL", request.descricao());
    assertEquals("Nota fiscal", request.tipoDocumento());
    assertEquals("NF-12345", request.numeroDocumento());
    assertEquals("EMPRESA TESTE", request.razaoSocialNome());
    assertEquals("123.456.789-00", request.cnpjCpf());
    assertEquals("Observacao", request.observacao());
  }

  @Test
  @DisplayName("Deve mapear Item para ItemResponse")
  void deveMapearItemParaItemResponse() {
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    LocalDate data = LocalDate.of(2026, Month.MARCH, 17);
    LocalDateTime horarioCriacao = LocalDateTime.of(2026, Month.MARCH, 17, 10, 15, 0);
    String caminhoArquivoPdf = "uploads/itens/item.pdf";
    Item item = new Item();
    item.setId(id);
    item.setValor(new BigDecimal("45.20"));
    item.setData(data);
    item.setHorarioCriacao(horarioCriacao);
    item.setCaminhoArquivoPdf(caminhoArquivoPdf);
    item.setTipo(TipoItem.RECEITA);
    item.setRoleNome("FINANCEIRO");
    item.setDescricao("ALUGUEL");
    item.setTipoDocumento("Fatura");
    item.setNumeroDocumento("FAT-2026-9");
    item.setRazaoSocialNome("EMPRESA TESTE");
    item.setCnpjCpf("12.345.678/0001-99");
    item.setObservacao("Observacao de teste");
    item.setVerificado(true);
    item.setFormaPagamento(FormaPagamentoItem.PARCELADO);
    ItemParcelaPagamento parcela1 = new ItemParcelaPagamento();
    parcela1.setId(UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111"));
    parcela1.setNumero(1);
    parcela1.setValorParcela(new BigDecimal("22.60"));
    parcela1.setPaga(true);
    parcela1.setContaOrigemPagamento(ContaOrigemPagamentoItem.CONTA_FEFC);
    parcela1.setCaminhoArquivoPdf("uploads/itens/parcela-1.pdf");
    parcela1.setItem(item);
    ItemParcelaPagamento parcela2 = new ItemParcelaPagamento();
    parcela2.setId(UUID.fromString("bbbbbbbb-2222-2222-2222-222222222222"));
    parcela2.setNumero(2);
    parcela2.setValorParcela(new BigDecimal("22.60"));
    parcela2.setPaga(false);
    parcela2.setItem(item);
    item.getParcelasPagamento().add(parcela1);
    item.getParcelasPagamento().add(parcela2);

    ItemResponse response = ItemResponse.from(item);

    assertEquals(id, response.id());
    assertEquals(new BigDecimal("45.20"), response.valor());
    assertEquals(data, response.data());
    assertEquals(horarioCriacao, response.horarioCriacao());
    assertEquals(caminhoArquivoPdf, response.caminhoArquivoPdf());
    assertEquals(TipoItem.RECEITA, response.tipo());
    assertEquals("FINANCEIRO", response.role());
    assertEquals("ALUGUEL", response.descricao());
    assertEquals("Fatura", response.tipoDocumento());
    assertEquals("FAT-2026-9", response.numeroDocumento());
    assertEquals("EMPRESA TESTE", response.razaoSocialNome());
    assertEquals("12.345.678/0001-99", response.cnpjCpf());
    assertEquals("Observacao de teste", response.observacao());
    assertTrue(response.verificado());
    assertEquals(FormaPagamentoItem.PARCELADO, response.pagamento().formaPagamento());
    assertEquals(2, response.pagamento().quantidadeParcelas());
    assertEquals(new BigDecimal("22.60"), response.pagamento().totalPago());
    assertEquals(
        ContaOrigemPagamentoItem.CONTA_FEFC,
        response.pagamento().parcelas().get(0).contaOrigemPagamento());
    assertEquals("parcela-1.pdf", response.pagamento().parcelas().get(0).nomeArquivoComprovante());
    assertTrue(response.pagamento().parcelas().get(0).temArquivoComprovante());
  }

  @Test
  @DisplayName("Deve comparar ItemUpsertRequest por conteudo do array")
  void deveCompararItemUpsertRequestPorConteudoDoArray() {
    LocalDate data = LocalDate.of(2026, Month.MARCH, 15);
    LocalDateTime horarioCriacao = LocalDateTime.of(2026, Month.MARCH, 15, 18, 0, 0);
    ItemUpsertRequest requestA =
        new ItemUpsertRequest(
            new BigDecimal("99.90"),
            data,
            horarioCriacao,
            List.of(new byte[] {1, 2, 3}),
            List.of("arquivo-1.pdf"),
            TipoItem.RECEITA,
            "FINANCEIRO",
            "ALUGUEL",
            "Nota fiscal",
            "NF-1",
            "EMPRESA A",
            "123.456.789-00",
            "Obs");
    ItemUpsertRequest requestB =
        new ItemUpsertRequest(
            new BigDecimal("99.90"),
            data,
            horarioCriacao,
            List.of(new byte[] {1, 2, 3}),
            List.of("arquivo-1.pdf"),
            TipoItem.RECEITA,
            "FINANCEIRO",
            "ALUGUEL",
            "Nota fiscal",
            "NF-1",
            "EMPRESA A",
            "123.456.789-00",
            "Obs");
    ItemUpsertRequest requestDiferente =
        new ItemUpsertRequest(
            new BigDecimal("99.90"),
            data,
            horarioCriacao,
            List.of(new byte[] {9, 9, 9}),
            List.of("arquivo-2.pdf"),
            TipoItem.RECEITA,
            "OPERADOR",
            "ALUGUEL",
            "Outros",
            "DOC-99",
            "EMPRESA B",
            "123.456.789-00",
            "Obs");

    assertEquals(requestA, Objects.requireNonNull(requestA));
    assertEquals(requestA, requestB);
    assertEquals(requestA.hashCode(), requestB.hashCode());
    assertNotEquals(requestA, requestDiferente);
    boolean equalsNull = requestA.equals(null);
    boolean equalsOutroTipo = requestA.equals("tipo-invalido");
    assertNotEquals(true, equalsNull);
    assertNotEquals(true, equalsOutroTipo);
    assertNotNull(requestA.toString());
    assertTrue(requestA.toString().contains("arquivosPdf=[[1, 2, 3]]"));
    assertTrue(requestA.toString().contains("nomesArquivos=[arquivo-1.pdf]"));
    assertTrue(requestA.toString().contains("role=FINANCEIRO"));
  }

  @Test
  @DisplayName("Deve fazer copia defensiva do array em ItemUpsertRequest")
  void deveFazerCopiaDefensivaDoArrayEmItemUpsertRequest() {
    byte[] arquivoPdf = new byte[] {1, 2, 3};
    ItemUpsertRequest request =
        new ItemUpsertRequest(
            new BigDecimal("10.00"),
            LocalDate.of(2026, Month.MARCH, 16),
            LocalDateTime.of(2026, Month.MARCH, 16, 10, 0),
            List.of(arquivoPdf),
            List.of("arquivo-1.pdf"),
            TipoItem.RECEITA,
            "FINANCEIRO",
            "ALUGUEL",
            "Boleto",
            "BOL-10",
            "EMPRESA TESTE",
            "123.456.789-00",
            "Obs");

    arquivoPdf[0] = 9;
    byte[] retorno = request.arquivosPdf().get(0);
    retorno[1] = 9;

    assertArrayEquals(new byte[] {1, 2, 3}, request.arquivosPdf().get(0));
  }

  @Test
  @DisplayName("Deve fazer copia defensiva em ItemArquivosUploadRequest")
  void deveFazerCopiaDefensivaEmItemArquivosUploadRequest() {
    byte[] pdf = new byte[] {1, 2, 3};
    ItemArquivosUploadRequest request =
        new ItemArquivosUploadRequest(List.of(pdf), List.of("arquivo.pdf"));

    pdf[0] = 9;
    byte[] retorno = request.arquivosPdf().get(0);
    retorno[1] = 9;

    assertArrayEquals(new byte[] {1, 2, 3}, request.arquivosPdf().get(0));
    assertEquals(List.of("arquivo.pdf"), request.nomesArquivos());
  }

  @Test
  @DisplayName("Deve fazer copia defensiva dos anexos de pagamento")
  void deveFazerCopiaDefensivaDosAnexosDePagamento() {
    byte[] arquivo = new byte[] {1, 2, 3};
    byte[] arquivoLegado = new byte[] {4, 5, 6};
    List<byte[]> arquivos = new java.util.ArrayList<>(List.of(arquivo));
    ItemPagamentoParcelaUpdateRequest parcela =
        new ItemPagamentoParcelaUpdateRequest(
            1,
            true,
            ContaOrigemPagamentoItem.CONTA_DC,
            new BigDecimal("10.00"),
            arquivos,
            List.of("parcela.pdf"),
            List.of(),
            false,
            arquivoLegado,
            "legado.pdf");
    ItemPagamentoUpdateRequest pagamento =
        new ItemPagamentoUpdateRequest(FormaPagamentoItem.AVISTA, 1, List.of(parcela));

    arquivo[0] = 9;
    arquivoLegado[0] = 9;
    arquivos.clear();
    parcela.arquivosPdf().getFirst()[1] = 9;
    parcela.arquivoPdf()[1] = 9;

    assertArrayEquals(new byte[] {1, 2, 3}, parcela.arquivosPdf().getFirst());
    assertArrayEquals(new byte[] {4, 5, 6}, parcela.arquivoPdf());
    assertEquals(1, pagamento.parcelas().size());
    List<ItemPagamentoParcelaUpdateRequest> parcelas = pagamento.parcelas();
    assertThrows(UnsupportedOperationException.class, parcelas::clear);

    ItemPagamentoParcelaUpdateRequest equivalente =
        new ItemPagamentoParcelaUpdateRequest(
            1,
            true,
            ContaOrigemPagamentoItem.CONTA_DC,
            new BigDecimal("10.00"),
            List.of(new byte[] {1, 2, 3}),
            List.of("parcela.pdf"),
            List.of(),
            false,
            new byte[] {4, 5, 6},
            "legado.pdf");
    ItemPagamentoParcelaUpdateRequest diferente =
        new ItemPagamentoParcelaUpdateRequest(
            1,
            true,
            ContaOrigemPagamentoItem.CONTA_DC,
            new BigDecimal("11.00"),
            List.of(new byte[] {1, 2, 3}),
            List.of("parcela.pdf"),
            List.of(),
            false,
            new byte[] {4, 5, 6},
            "legado.pdf");

    assertEquals(parcela, equivalente);
    assertEquals(parcela.hashCode(), equivalente.hashCode());
    assertNotEquals(parcela, diferente);
    assertTrue(parcela.toString().contains("arquivoPdf=[4, 5, 6]"));
  }

  @Test
  @DisplayName("Deve fazer copia defensiva em ItemListPageResponse")
  void deveFazerCopiaDefensivaEmItemListPageResponse() {
    ItemListResponse item =
        new ItemListResponse(
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            new BigDecimal("12.34"),
            LocalDate.of(2026, Month.APRIL, 1),
            LocalDateTime.of(2026, Month.APRIL, 1, 9, 0),
            TipoItem.DESPESA,
            "FINANCEIRO",
            "SERVICOS",
            "EMPRESA TESTE",
            "123.456.789-00",
            false,
            false);
    List<ItemListResponse> items = new java.util.ArrayList<>(List.of(item));
    ItemListPageResponse response = new ItemListPageResponse(items, 1, 10, 1, 1, false, false);

    items.clear();
    List<ItemListResponse> immutableItems = response.items();

    assertEquals(1, immutableItems.size());
    assertThrows(UnsupportedOperationException.class, () -> immutableItems.add(item));
  }

  @Test
  @DisplayName("Deve mapear Item para resumo sem expor documento completo ou observacao")
  void deveMapearItemParaItemListResponseComDocumentoMascarado() {
    Item comArquivo = new Item();
    comArquivo.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
    comArquivo.setValor(new BigDecimal("150.00"));
    comArquivo.setData(LocalDate.of(2026, Month.APRIL, 2));
    comArquivo.setHorarioCriacao(LocalDateTime.of(2026, Month.APRIL, 2, 8, 30));
    comArquivo.setCaminhoArquivoPdf("uploads/itens/com-arquivo.pdf");
    comArquivo.setTipo(TipoItem.DESPESA);
    comArquivo.setRoleNome("FINANCEIRO");
    comArquivo.setDescricao("AGUA");
    comArquivo.setRazaoSocialNome("BONAFONTE");
    comArquivo.setCnpjCpf("20.783.964/2307-84");
    comArquivo.setObservacao("Pago");
    comArquivo.setVerificado(true);

    Item semArquivo = new Item();
    semArquivo.setId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
    semArquivo.setValor(new BigDecimal("75.00"));
    semArquivo.setData(LocalDate.of(2026, Month.APRIL, 3));
    semArquivo.setHorarioCriacao(LocalDateTime.of(2026, Month.APRIL, 3, 9, 45));
    semArquivo.setCaminhoArquivoPdf("   ");
    semArquivo.setTipo(TipoItem.RECEITA);
    semArquivo.setRoleNome("OPERADOR");
    semArquivo.setDescricao("CONTA DC");
    semArquivo.setRazaoSocialNome("GOV SP");
    semArquivo.setCnpjCpf("10.237.681/0238-76");
    semArquivo.setObservacao(null);
    semArquivo.setVerificado(false);

    ItemListResponse responseComArquivo = ItemListResponse.from(comArquivo);
    ItemListResponse responseSemArquivo = ItemListResponse.from(semArquivo);

    assertEquals(comArquivo.getId(), responseComArquivo.id());
    assertEquals("BONAFONTE", responseComArquivo.razaoSocialNome());
    assertEquals("**.***.***/****-84", responseComArquivo.cnpjCpfMascarado());
    assertTrue(responseComArquivo.temArquivos());
    assertEquals(semArquivo.getId(), responseSemArquivo.id());
    assertEquals("CONTA DC", responseSemArquivo.descricao());
    assertEquals(false, responseSemArquivo.temArquivos());
  }
}
