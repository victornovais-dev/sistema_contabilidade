package com.sistema_contabilidade.item.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.item.model.ContaOrigemPagamentoItem;
import com.sistema_contabilidade.item.model.FormaPagamentoItem;
import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.ItemArquivo;
import com.sistema_contabilidade.item.model.ItemAuditoria;
import com.sistema_contabilidade.item.model.ItemAuditoriaAcao;
import com.sistema_contabilidade.item.model.ItemParcelaPagamento;
import com.sistema_contabilidade.item.model.ItemParcelaPagamentoArquivo;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemAuditoriaRepository;
import com.sistema_contabilidade.security.service.RequestFingerprintService;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItemAuditService unit tests")
class ItemAuditServiceTest {

  private static final UUID ITEM_ID = UUID.fromString("70000000-0000-0000-0000-000000000002");
  private static final UUID ATOR_ID = UUID.fromString("70000000-0000-0000-0000-000000000003");
  private static final Instant AGORA = Instant.parse("2026-09-04T15:00:00Z");
  private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";

  @Mock private ItemAuditoriaRepository auditoriaRepository;
  @Mock private UsuarioRepository usuarioRepository;
  @Mock private RequestFingerprintService requestFingerprintService;
  @Mock private ArquivoStorageService arquivoStorageService;
  @Mock private Authentication authentication;
  @Mock private HttpServletRequest request;

  private ObjectMapper objectMapper;
  private ItemAuditService service;
  private Usuario ator;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    service =
        new ItemAuditService(
            auditoriaRepository,
            usuarioRepository,
            requestFingerprintService,
            arquivoStorageService,
            objectMapper,
            Clock.fixed(AGORA, ZoneOffset.UTC));
    ator = new Usuario();
    ator.setId(ATOR_ID);
    ator.setNome("Auditor Teste");
    ator.setEmail("auditor@example.com");
  }

  @AfterEach
  void limparMdc() {
    MDC.clear();
  }

  @Test
  @DisplayName("Deve registrar somente campos alterados com ator e contexto da requisicao")
  void deveRegistrarSomenteCamposAlteradosComAtorEContexto() throws Exception {
    Item item = novoItem();
    ItemAuditSnapshot antes = service.capturarSnapshot(item);
    item.setValor(new BigDecimal("120.00"));
    item.setObservacao("Observacao nova");
    ItemAuditSnapshot depois = service.capturarSnapshot(item);
    prepararContextoAutenticado("203.0.113.10", "audit-test-agent");
    MDC.put("trace.id", TRACE_ID);

    Optional<ItemAuditoria> resultado =
        service.registrar(ItemAuditoriaAcao.ALTERADO, antes, depois, authentication, request);

    assertTrue(resultado.isPresent());
    ArgumentCaptor<ItemAuditoria> captor = ArgumentCaptor.forClass(ItemAuditoria.class);
    verify(auditoriaRepository).save(captor.capture());
    ItemAuditoria auditoria = captor.getValue();
    assertEquals(ITEM_ID, auditoria.getItemId());
    assertEquals(ItemAuditoriaAcao.ALTERADO, auditoria.getAcao());
    assertEquals(ATOR_ID, auditoria.getAtorId());
    assertEquals("Auditor Teste", auditoria.getAtorNome());
    assertEquals("auditor@example.com", auditoria.getAtorEmail());
    assertEquals(AGORA, auditoria.getOcorridoEm());
    assertEquals("203.0.113.10", auditoria.getIpOrigem());
    assertEquals("audit-test-agent", auditoria.getUserAgent());
    assertEquals(TRACE_ID, auditoria.getTraceId());
    assertNull(auditoria.getSnapshotItem());

    JsonNode alteracoes = objectMapper.readTree(auditoria.getAlteracoes());
    assertEquals(2, alteracoes.size());
    assertEquals(
        0, new BigDecimal("100.00").compareTo(alteracoes.get("valor").get("antes").decimalValue()));
    assertEquals(
        0,
        new BigDecimal("120.00").compareTo(alteracoes.get("valor").get("depois").decimalValue()));
    assertEquals("Observacao anterior", alteracoes.get("observacao").get("antes").stringValue());
    assertEquals("Observacao nova", alteracoes.get("observacao").get("depois").stringValue());
    assertFalse(alteracoes.has("descricao"));
    verify(usuarioRepository).findByEmail("auditor@example.com");
    verify(requestFingerprintService).resolveClientIp(request);
  }

  @Test
  @DisplayName("Deve guardar snapshot de criacao somente com metadados dos PDFs")
  void deveGuardarSnapshotCriacaoSomenteComMetadadosDosPdfs() throws Exception {
    Item item = novoItemComArquivosEPagamento();
    ItemAuditSnapshot depois = service.capturarSnapshot(item);
    prepararContextoAutenticado("2001:db8::1", "browser-test");
    MDC.put("trace.id", TRACE_ID);

    service.registrar(ItemAuditoriaAcao.CRIADO, null, depois, authentication, request);

    ArgumentCaptor<ItemAuditoria> captor = ArgumentCaptor.forClass(ItemAuditoria.class);
    verify(auditoriaRepository).save(captor.capture());
    ItemAuditoria auditoria = captor.getValue();
    assertEquals("{}", auditoria.getAlteracoes());
    assertNotNull(auditoria.getSnapshotItem());
    JsonNode snapshot = objectMapper.readTree(auditoria.getSnapshotItem());
    assertEquals(1, snapshot.get("arquivos").size());
    assertEquals("nota.pdf", snapshot.get("arquivos").get(0).get("nome").stringValue());
    assertEquals(
        "itens/nota.pdf", snapshot.get("arquivos").get(0).get("chaveArmazenamento").stringValue());
    assertEquals(
        "parcela.pdf",
        snapshot
            .get("pagamento")
            .get("parcelas")
            .get(0)
            .get("arquivos")
            .get(0)
            .get("nome")
            .stringValue());
    assertFalse(auditoria.getSnapshotItem().contains("arquivoPdf"));
    assertFalse(auditoria.getSnapshotItem().contains("JVBER"));
  }

  @Test
  @DisplayName("Nao deve criar evento quando nenhum campo mudou")
  void naoDeveCriarEventoQuandoNenhumCampoMudou() {
    ItemAuditSnapshot snapshot = service.capturarSnapshot(novoItem());

    Optional<ItemAuditoria> resultado =
        service.registrar(ItemAuditoriaAcao.ALTERADO, snapshot, snapshot, authentication, request);

    assertTrue(resultado.isEmpty());
    verify(auditoriaRepository, never()).save(any());
    verifyNoInteractions(usuarioRepository, requestFingerprintService);
  }

  @Test
  @DisplayName("Deve limitar metadados controlados pelo cliente")
  void deveLimitarMetadadosControladosPeloCliente() {
    Item item = novoItem();
    ItemAuditSnapshot antes = service.capturarSnapshot(item);
    item.setVerificado(true);
    ItemAuditSnapshot depois = service.capturarSnapshot(item);
    prepararContextoAutenticado("1".repeat(200), "A".repeat(2000));

    service.registrar(
        ItemAuditoriaAcao.VERIFICACAO_ALTERADA, antes, depois, authentication, request);

    ArgumentCaptor<ItemAuditoria> captor = ArgumentCaptor.forClass(ItemAuditoria.class);
    verify(auditoriaRepository).save(captor.capture());
    assertEquals(64, captor.getValue().getIpOrigem().length());
    assertEquals(1024, captor.getValue().getUserAgent().length());
    assertEquals(32, captor.getValue().getTraceId().length());
  }

  @Test
  @DisplayName("Deve rejeitar ator nao autenticado")
  void deveRejeitarAtorNaoAutenticado() {
    Item item = novoItem();
    ItemAuditSnapshot antes = service.capturarSnapshot(item);
    item.setVerificado(true);
    ItemAuditSnapshot depois = service.capturarSnapshot(item);
    when(authentication.isAuthenticated()).thenReturn(false);

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () ->
                service.registrar(
                    ItemAuditoriaAcao.VERIFICACAO_ALTERADA,
                    antes,
                    depois,
                    authentication,
                    request));

    assertEquals(401, exception.getStatusCode().value());
    verify(auditoriaRepository, never()).save(any());
  }

  @Test
  @DisplayName("Deve exigir transacao externa para salvar auditoria junto da mutacao")
  void deveExigirTransacaoExternaParaSalvarAuditoriaJuntoDaMutacao() throws Exception {
    Transactional transactional =
        ItemAuditService.class
            .getMethod(
                "registrar",
                ItemAuditoriaAcao.class,
                ItemAuditSnapshot.class,
                ItemAuditSnapshot.class,
                Authentication.class,
                HttpServletRequest.class)
            .getAnnotation(Transactional.class);

    assertNotNull(transactional);
    assertEquals(Propagation.MANDATORY, transactional.propagation());
  }

  private void prepararContextoAutenticado(String ip, String userAgent) {
    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getName()).thenReturn("auditor@example.com");
    when(usuarioRepository.findByEmail("auditor@example.com")).thenReturn(Optional.of(ator));
    when(requestFingerprintService.resolveClientIp(request)).thenReturn(ip);
    when(request.getHeader("User-Agent")).thenReturn(userAgent);
    when(auditoriaRepository.save(any(ItemAuditoria.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private Item novoItem() {
    Item item = new Item();
    item.setId(ITEM_ID);
    item.setValor(new BigDecimal("100.00"));
    item.setData(LocalDate.of(2026, Month.SEPTEMBER, 4));
    item.setHorarioCriacao(LocalDateTime.of(2026, Month.SEPTEMBER, 4, 11, 30));
    item.setTipo(TipoItem.DESPESA);
    item.setRoleNome("CAMPANHA TESTE");
    item.setDescricao("SERVICOS");
    item.setTipoDocumento("Nota fiscal");
    item.setNumeroDocumento("NF-123");
    item.setRazaoSocialNome("Fornecedor Teste");
    item.setCnpjCpf("12.345.678/0001-99");
    item.setObservacao("Observacao anterior");
    item.setCriadoPor(ator);
    return item;
  }

  private Item novoItemComArquivosEPagamento() {
    Item item = novoItem();
    item.setCaminhoArquivoPdf("itens/nota.pdf");
    ItemArquivo arquivo = new ItemArquivo();
    arquivo.setId(UUID.fromString("70000000-0000-0000-0000-000000000004"));
    arquivo.setItem(item);
    arquivo.setCaminhoArquivoPdf("itens/nota.pdf");
    item.getArquivos().add(arquivo);

    ItemParcelaPagamento parcela = new ItemParcelaPagamento();
    parcela.setId(UUID.fromString("70000000-0000-0000-0000-000000000005"));
    parcela.setItem(item);
    parcela.setNumero(1);
    parcela.setValorParcela(new BigDecimal("100.00"));
    parcela.setPaga(true);
    parcela.setContaOrigemPagamento(ContaOrigemPagamentoItem.CONTA_DC);
    ItemParcelaPagamentoArquivo arquivoPagamento = new ItemParcelaPagamentoArquivo();
    arquivoPagamento.setId(UUID.fromString("70000000-0000-0000-0000-000000000006"));
    arquivoPagamento.setParcelaPagamento(parcela);
    arquivoPagamento.setCaminhoArquivoPdf("pagamentos/parcela.pdf");
    parcela.getArquivosComprovantes().add(arquivoPagamento);
    item.setFormaPagamento(FormaPagamentoItem.AVISTA);
    item.getParcelasPagamento().add(parcela);

    when(arquivoStorageService.resolverNomeArquivo(anyString()))
        .thenAnswer(
            invocation -> {
              String chave = invocation.getArgument(0);
              return chave.substring(chave.lastIndexOf('/') + 1);
            });
    return item;
  }
}
