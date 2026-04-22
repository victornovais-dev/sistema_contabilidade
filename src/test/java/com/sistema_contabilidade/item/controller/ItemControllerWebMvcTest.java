package com.sistema_contabilidade.item.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sistema_contabilidade.item.config.ItemTipoDocumentoCatalog;
import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.ItemArquivo;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemArquivoRepository;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.item.service.ArquivoStorageService;
import com.sistema_contabilidade.item.service.ItemDescricaoService;
import com.sistema_contabilidade.item.service.ItemExpenseLimitService;
import com.sistema_contabilidade.item.service.ItemListService;
import com.sistema_contabilidade.item.service.ItemTipoDocumentoService;
import com.sistema_contabilidade.notificacao.service.NotificacaoService;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(ItemController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ItemController WebMvc tests")
class ItemControllerWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ItemRepository itemRepository;
  @MockitoBean private ItemArquivoRepository itemArquivoRepository;
  @MockitoBean private ArquivoStorageService itemArquivoStorageService;
  @MockitoBean private ItemDescricaoService itemDescricaoService;
  @MockitoBean private ItemTipoDocumentoService itemTipoDocumentoService;
  @MockitoBean private ItemExpenseLimitService itemExpenseLimitService;
  @MockitoBean private ItemListService itemListService;
  @MockitoBean private NotificacaoService notificacaoService;
  @MockitoBean private RoleRepository roleRepository;
  @MockitoBean private UsuarioRepository usuarioRepository;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private CustomUserDetailsService customUserDetailsService;

  @Test
  @DisplayName("Deve retornar lista de itens")
  void listarTodosDeveRetornarOk() throws Exception {
    Item item = new Item();
    item.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    item.setValor(new BigDecimal("10.00"));
    item.setData(LocalDate.of(2026, 3, 15));
    item.setHorarioCriacao(LocalDateTime.of(2026, 3, 15, 12, 0, 0));
    item.setCaminhoArquivoPdf("uploads/itens/item-lista.pdf");
    item.setTipo(TipoItem.RECEITA);
    item.setDescricao("ALUGUEL");
    item.setRazaoSocialNome("EMPRESA TESTE");
    item.setCnpjCpf("12.345.678/0001-99");
    item.setObservacao("Observacao de teste");
    when(itemListService.listarItens(any(), eq(null)))
        .thenReturn(
            List.of(
                new com.sistema_contabilidade.item.dto.ItemListResponse(
                    item.getId(),
                    item.getValor(),
                    item.getData(),
                    item.getHorarioCriacao(),
                    item.getCaminhoArquivoPdf(),
                    item.getTipo(),
                    "ADMIN",
                    item.getDescricao(),
                    item.getRazaoSocialNome(),
                    item.getCnpjCpf(),
                    item.getObservacao(),
                    false,
                    true)));

    mockMvc
        .perform(get("/api/v1/itens").with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].valor").value(10.00))
        .andExpect(jsonPath("$[0].tipo").value("RECEITA"));
  }

  @Test
  @DisplayName("Deve filtrar itens por role selecionada do proprio usuario")
  void listarTodosDeveFiltrarPorRoleSelecionada() throws Exception {
    Item item = new Item();
    item.setId(UUID.fromString("12121212-1111-1111-1111-111111111111"));
    item.setTipo(TipoItem.DESPESA);
    item.setDescricao("SERVICOS");
    when(itemListService.listarItens(any(), eq("financeiro")))
        .thenReturn(
            List.of(
                new com.sistema_contabilidade.item.dto.ItemListResponse(
                    item.getId(),
                    null,
                    null,
                    null,
                    null,
                    item.getTipo(),
                    "FINANCEIRO",
                    item.getDescricao(),
                    null,
                    null,
                    null,
                    false,
                    false)));

    mockMvc
        .perform(
            get("/api/v1/itens")
                .param("role", "financeiro")
                .with(authComRoles("multirole@email.com", "FINANCEIRO", "OPERADOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].descricao").value("SERVICOS"));
  }

  @Test
  @DisplayName("Deve permitir admin filtrar itens por qualquer role")
  void listarTodosDevePermitirAdminFiltrarPorQualquerRole() throws Exception {
    Item item = new Item();
    item.setId(UUID.fromString("34343434-1111-1111-1111-111111111111"));
    item.setTipo(TipoItem.RECEITA);
    item.setDescricao("ALUGUEL");
    when(itemListService.listarItens(any(), eq("tarcisio")))
        .thenReturn(
            List.of(
                new com.sistema_contabilidade.item.dto.ItemListResponse(
                    item.getId(),
                    null,
                    null,
                    null,
                    null,
                    item.getTipo(),
                    "TARCISIO",
                    item.getDescricao(),
                    null,
                    null,
                    null,
                    false,
                    false)));

    mockMvc
        .perform(
            get("/api/v1/itens")
                .param("role", "tarcisio")
                .with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].descricao").value("ALUGUEL"));
  }

  @Test
  @DisplayName("Deve exigir role ao criar item para usuario com multiplas roles")
  void criarDeveExigirRoleParaUsuarioComMultiplasRoles() throws Exception {
    when(usuarioRepository.findByEmail("multirole@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("multirole", "FINANCEIRO", "OPERADOR")));

    mockMvc
        .perform(
            post("/api/v1/itens")
                .with(authComRoles("multirole@email.com", "FINANCEIRO", "OPERADOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":120.50,
                      "data":"2026-03-15",
                      "horarioCriacao":"2026-03-15T18:00:00",
                      "arquivosPdf":["cGRm"],
                      "nomesArquivos":["documento-1.pdf"],
                      "tipo":"DESPESA",
                      "descricao":"SERVICOS"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Deve retornar 403 quando usuario filtrar por role que nao possui")
  void listarTodosDeveRetornarForbiddenQuandoRoleNaoPertenceAoUsuario() throws Exception {
    when(itemListService.listarItens(any(), eq("financeiro")))
        .thenThrow(
            new ResponseStatusException(
                HttpStatus.FORBIDDEN, "A role selecionada nao pertence ao usuario autenticado."));

    mockMvc
        .perform(
            get("/api/v1/itens")
                .param("role", "financeiro")
                .with(authComRoles("operador@email.com", "OPERADOR")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Deve retornar roles disponiveis do usuario autenticado")
  void listarRolesDisponiveisDeveRetornarRolesDoUsuario() throws Exception {
    when(itemListService.listarRolesDisponiveis(any()))
        .thenReturn(List.of("FINANCEIRO", "OPERADOR"));

    mockMvc
        .perform(get("/api/v1/itens/roles").with(authComRoles("multirole@email.com", "FINANCEIRO")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value("FINANCEIRO"))
        .andExpect(jsonPath("$[1]").value("OPERADOR"));
  }

  @Test
  @DisplayName("Deve retornar todas as roles para admin no filtro de itens")
  void listarRolesDisponiveisDeveRetornarTodasAsRolesParaAdmin() throws Exception {
    when(itemListService.listarRolesDisponiveis(any())).thenReturn(List.of("FINANCEIRO"));

    mockMvc
        .perform(get("/api/v1/itens/roles").with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value("FINANCEIRO"));
  }

  @Test
  @DisplayName("Deve retornar descricoes por tipo vindas do backend")
  void listarDescricoesPorTipoDeveRetornarOk() throws Exception {
    when(itemDescricaoService.listarDescricoesPorTipo(TipoItem.RECEITA))
        .thenReturn(List.of("CONTA DC", "CONTA FEFC", "CONTA FP", "ESTIMÁVEL"));

    mockMvc
        .perform(
            get("/api/v1/itens/descricoes")
                .param("tipo", "RECEITA")
                .with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value("CONTA DC"))
        .andExpect(jsonPath("$[3]").value("ESTIMÁVEL"));
  }

  @Test
  @DisplayName("Deve retornar lista vazia quando usuario nao possui roles")
  void listarTodosDeveRetornarVazioQuandoUsuarioNaoPossuiRoles() throws Exception {
    when(itemListService.listarItens(any(), eq(null))).thenReturn(List.of());

    mockMvc
        .perform(get("/api/v1/itens").with(authComRoles("semroles@email.com")))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
  }

  @Test
  @DisplayName("Deve retornar tipos de documento vindos do backend")
  void listarTiposDocumentoDeveRetornarOk() throws Exception {
    when(itemTipoDocumentoService.listarTiposDocumento())
        .thenReturn(List.of("Nota fiscal", "Fatura", "Boleto", "Outros"));

    mockMvc
        .perform(
            get("/api/v1/itens/tipos-documento").with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value("Nota fiscal"))
        .andExpect(jsonPath("$[3]").value("Outros"));
  }

  @Test
  @DisplayName("Deve retornar tipos de documento por tipo de lancamento")
  void listarTiposDocumentoDeveRetornarPorTipo() throws Exception {
    when(itemTipoDocumentoService.listarTiposDocumentoPorTipo(TipoItem.RECEITA))
        .thenReturn(List.of("Pix", "Transferência", "Cheque", "Dinheiro"));

    mockMvc
        .perform(
            get("/api/v1/itens/tipos-documento")
                .param("tipo", "RECEITA")
                .with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value("Pix"))
        .andExpect(jsonPath("$[1]").value("Transferência"))
        .andExpect(jsonPath("$[3]").value("Dinheiro"));
  }

  @Test
  @DisplayName("Deve retornar catalogo padrao quando service de tipos de documento falhar")
  void listarTiposDocumentoDeveRetornarCatalogoPadraoQuandoServiceFalhar() throws Exception {
    when(itemTipoDocumentoService.listarTiposDocumento())
        .thenThrow(new IllegalStateException("cache indisponivel"));

    mockMvc
        .perform(
            get("/api/v1/itens/tipos-documento").with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.length()").value(ItemTipoDocumentoCatalog.defaultDocumentTypes().size()))
        .andExpect(jsonPath("$[0]").value("Nota fiscal"))
        .andExpect(jsonPath("$[3]").value("Outros"));
  }

  @Test
  @DisplayName("Deve criar item")
  void criarDeveRetornarCreated() throws Exception {
    Item item = new Item();
    item.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    item.setValor(new BigDecimal("120.50"));
    item.setData(LocalDate.of(2026, 3, 15));
    item.setHorarioCriacao(LocalDateTime.of(2026, 3, 15, 18, 0, 0));
    item.setCaminhoArquivoPdf("uploads/itens/item-criado.pdf");
    item.setTipo(TipoItem.DESPESA);
    item.setDescricao("SERVICOS");
    item.setTipoDocumento("Nota fiscal");
    item.setNumeroDocumento("2026001");
    item.setRazaoSocialNome("FORNECEDOR TESTE");
    item.setCnpjCpf("123.456.789-00");
    item.setObservacao("Observacao do teste");
    ItemArquivo arquivoCriado = new ItemArquivo();
    arquivoCriado.setCaminhoArquivoPdf("uploads/itens/item-criado.pdf");
    arquivoCriado.setItem(item);
    item.getArquivos().add(arquivoCriado);
    when(itemArquivoStorageService.salvarPdfs(
            org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(List.of("uploads/itens/item-criado.pdf"));
    when(itemRepository.save(org.mockito.ArgumentMatchers.any(Item.class))).thenReturn(item);
    when(usuarioRepository.findByEmail("operador@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("OPERADOR", "OPERATOR")));

    mockMvc
        .perform(
            post("/api/v1/itens")
                .with(authComRoles("operador@email.com", "OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":120.50,
                      "data":"2026-03-15",
                      "horarioCriacao":"2026-03-15T18:00:00",
                      "arquivosPdf":["cGRm","cGRmMg=="],
                      "nomesArquivos":["documento-1.pdf","documento-2.pdf"],
                      "tipo":"DESPESA",
                      "tipoDocumento":"Nota fiscal",
                      "numeroDocumento":"2026001",
                      "descricao":"SERVICOS",
                      "razaoSocialNome":"FORNECEDOR TESTE",
                      "cnpjCpf":"123.456.789-00",
                      "observacao":"Observacao do teste"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.valor").value(120.50))
        .andExpect(jsonPath("$.caminhoArquivoPdf").value("uploads/itens/item-criado.pdf"))
        .andExpect(jsonPath("$.tipo").value("DESPESA"))
        .andExpect(jsonPath("$.tipoDocumento").value("Nota fiscal"))
        .andExpect(jsonPath("$.numeroDocumento").value("2026001"))
        .andExpect(jsonPath("$.descricao").value("SERVICOS"))
        .andExpect(jsonPath("$.razaoSocialNome").value("FORNECEDOR TESTE"))
        .andExpect(jsonPath("$.cnpjCpf").value("123.456.789-00"))
        .andExpect(jsonPath("$.observacao").value("Observacao do teste"))
        .andExpect(jsonPath("$.arquivosPdf[0]").value("uploads/itens/item-criado.pdf"));

    verify(notificacaoService, never()).registrarReceitaLancada(any(Item.class));
    verify(notificacaoService, never()).sincronizarComItem(any(Item.class));
  }

  @Test
  @DisplayName("Deve criar notificacao quando item criado for receita")
  void criarDeveRegistrarNotificacaoQuandoItemForReceita() throws Exception {
    Item item = new Item();
    item.setId(UUID.fromString("21111111-1111-1111-1111-111111111111"));
    item.setValor(new BigDecimal("100000.00"));
    item.setData(LocalDate.of(2026, 4, 14));
    item.setHorarioCriacao(LocalDateTime.of(2026, 4, 14, 11, 0, 0));
    item.setCaminhoArquivoPdf("uploads/itens/receita.pdf");
    item.setTipo(TipoItem.RECEITA);
    item.setDescricao("CONTA DC");
    ItemArquivo arquivoCriado = new ItemArquivo();
    arquivoCriado.setCaminhoArquivoPdf("uploads/itens/receita.pdf");
    arquivoCriado.setItem(item);
    item.getArquivos().add(arquivoCriado);
    when(itemArquivoStorageService.salvarPdfs(
            org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(List.of("uploads/itens/receita.pdf"));
    when(itemRepository.save(org.mockito.ArgumentMatchers.any(Item.class))).thenReturn(item);
    when(usuarioRepository.findByEmail("manager@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("manager", "MANAGER")));

    mockMvc
        .perform(
            post("/api/v1/itens")
                .with(authComRoles("manager@email.com", "MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":100000.00,
                      "data":"2026-04-14",
                      "horarioCriacao":"2026-04-14T11:00:00",
                      "arquivosPdf":["cGRm"],
                      "nomesArquivos":["receita.pdf"],
                      "tipo":"RECEITA",
                      "descricao":"CONTA DC",
                      "role":"MANAGER"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tipo").value("RECEITA"))
        .andExpect(jsonPath("$.descricao").value("CONTA DC"));

    verify(notificacaoService).registrarReceitaLancada(any(Item.class));
  }

  @Test
  @DisplayName("Deve retornar 400 ao criar despesa que excede limite de categoria")
  void criarDeveRetornarBadRequestQuandoDespesaExcederLimiteDeCategoria() throws Exception {
    when(usuarioRepository.findByEmail("operador@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("OPERADOR", "OPERATOR")));
    doThrow(
            new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Nao e permitido adicionar esta despesa. Alimentacao pode representar no maximo 10% do total de despesas."))
        .when(itemExpenseLimitService)
        .validarLimiteDespesa(any(), eq("OPERATOR"), eq(null));

    mockMvc
        .perform(
            post("/api/v1/itens")
                .with(authComRoles("operador@email.com", "OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":120.50,
                      "data":"2026-03-15",
                      "horarioCriacao":"2026-03-15T18:00:00",
                      "arquivosPdf":["cGRm"],
                      "nomesArquivos":["documento.pdf"],
                      "tipo":"DESPESA",
                      "descricao":"ALIMENTAÇÃO"
                    }
                    """))
        .andExpect(status().isBadRequest());

    verify(itemRepository, never()).save(any(Item.class));
  }

  @Test
  @DisplayName("Deve retornar 400 quando CPF ja existir em outro item")
  void criarDeveRetornarBadRequestQuandoCpfJaExistir() throws Exception {
    when(itemRepository.countByDocumentoNormalizado("12345678900")).thenReturn(1L);
    when(usuarioRepository.findByEmail("operador@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("OPERADOR", "OPERATOR")));

    mockMvc
        .perform(
            post("/api/v1/itens")
                .with(authComRoles("operador@email.com", "OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":120.50,
                      "data":"2026-03-15",
                      "horarioCriacao":"2026-03-15T18:00:00",
                      "arquivosPdf":["cGRm"],
                      "nomesArquivos":["documento.pdf"],
                      "tipo":"DESPESA",
                      "tipoDocumento":"Nota fiscal",
                      "numeroDocumento":"2026001",
                      "descricao":"SERVICOS",
                      "razaoSocialNome":"FORNECEDOR TESTE",
                      "cnpjCpf":"123.456.789-00"
                    }
                    """))
        .andExpect(status().isBadRequest());

    verify(itemRepository, never()).save(any(Item.class));
  }

  @Test
  @DisplayName("Deve permitir criar item com CNPJ repetido")
  void criarDevePermitirCnpjRepetido() throws Exception {
    Item item = new Item();
    item.setId(UUID.fromString("31111111-1111-1111-1111-111111111111"));
    item.setValor(new BigDecimal("120.50"));
    item.setData(LocalDate.of(2026, 3, 15));
    item.setHorarioCriacao(LocalDateTime.of(2026, 3, 15, 18, 0, 0));
    item.setCaminhoArquivoPdf("uploads/itens/item-cnpj.pdf");
    item.setTipo(TipoItem.DESPESA);
    item.setDescricao("SERVICOS");
    item.setTipoDocumento("Nota fiscal");
    item.setNumeroDocumento("2026001");
    item.setRazaoSocialNome("FORNECEDOR TESTE");
    item.setCnpjCpf("12.345.678/0001-99");
    ItemArquivo arquivoCriado = new ItemArquivo();
    arquivoCriado.setCaminhoArquivoPdf("uploads/itens/item-cnpj.pdf");
    arquivoCriado.setItem(item);
    item.getArquivos().add(arquivoCriado);
    when(itemArquivoStorageService.salvarPdfs(any(), any()))
        .thenReturn(List.of("uploads/itens/item-cnpj.pdf"));
    when(itemRepository.save(any(Item.class))).thenReturn(item);
    when(usuarioRepository.findByEmail("operador@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("OPERADOR", "OPERATOR")));

    mockMvc
        .perform(
            post("/api/v1/itens")
                .with(authComRoles("operador@email.com", "OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":120.50,
                      "data":"2026-03-15",
                      "horarioCriacao":"2026-03-15T18:00:00",
                      "arquivosPdf":["cGRm"],
                      "nomesArquivos":["documento.pdf"],
                      "tipo":"DESPESA",
                      "tipoDocumento":"Nota fiscal",
                      "numeroDocumento":"2026001",
                      "descricao":"SERVICOS",
                      "razaoSocialNome":"FORNECEDOR TESTE",
                      "cnpjCpf":"12.345.678/0001-99"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.cnpjCpf").value("12.345.678/0001-99"));
  }

  @Test
  @DisplayName("Deve retornar 400 quando valor ultrapassar dez milhoes")
  void criarDeveRetornarBadRequestQuandoValorUltrapassarDezMilhoes() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/itens")
                .with(authComRoles("operador@email.com", "OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":10000000.01,
                      "data":"2026-03-15",
                      "horarioCriacao":"2026-03-15T18:00:00",
                      "arquivosPdf":["cGRm"],
                      "nomesArquivos":["documento-1.pdf"],
                      "tipo":"DESPESA",
                      "tipoDocumento":"Nota fiscal",
                      "numeroDocumento":"12345",
                      "descricao":"SERVICOS"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Deve retornar 400 quando razao social ultrapassar cento e cinquenta caracteres")
  void criarDeveRetornarBadRequestQuandoRazaoSocialUltrapassarCentoECinquentaCaracteres()
      throws Exception {
    String razaoSocialInvalida = "A".repeat(151);

    mockMvc
        .perform(
            post("/api/v1/itens")
                .with(authComRoles("operador@email.com", "OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":120.50,
                      "data":"2026-03-15",
                      "horarioCriacao":"2026-03-15T18:00:00",
                      "arquivosPdf":["cGRm"],
                      "nomesArquivos":["documento-1.pdf"],
                      "tipo":"DESPESA",
                      "tipoDocumento":"Nota fiscal",
                      "numeroDocumento":"12345",
                      "descricao":"SERVICOS",
                      "razaoSocialNome":"%s"
                    }
                    """
                        .formatted(razaoSocialInvalida)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Deve retornar 400 quando numero do documento nao for numerico")
  void criarDeveRetornarBadRequestQuandoNumeroDocumentoNaoForNumerico() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/itens")
                .with(authComRoles("operador@email.com", "OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":120.50,
                      "data":"2026-03-15",
                      "horarioCriacao":"2026-03-15T18:00:00",
                      "arquivosPdf":["cGRm"],
                      "nomesArquivos":["documento-1.pdf"],
                      "tipo":"DESPESA",
                      "tipoDocumento":"Nota fiscal",
                      "numeroDocumento":"NF-123",
                      "descricao":"SERVICOS"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Deve retornar 400 quando numero do documento ultrapassar cinquenta caracteres")
  void criarDeveRetornarBadRequestQuandoNumeroDocumentoUltrapassarCinquentaCaracteres()
      throws Exception {
    String numeroDocumentoInvalido = "1".repeat(51);

    mockMvc
        .perform(
            post("/api/v1/itens")
                .with(authComRoles("operador@email.com", "OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":120.50,
                      "data":"2026-03-15",
                      "horarioCriacao":"2026-03-15T18:00:00",
                      "arquivosPdf":["cGRm"],
                      "nomesArquivos":["documento-1.pdf"],
                      "tipo":"DESPESA",
                      "tipoDocumento":"Nota fiscal",
                      "numeroDocumento":"%s",
                      "descricao":"SERVICOS"
                    }
                    """
                        .formatted(numeroDocumentoInvalido)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Deve retornar 400 ao criar conta financeira sem anexo")
  void criarDeveRetornarBadRequestQuandoContaFinanceiraNaoTiverAnexo() throws Exception {
    when(usuarioRepository.findByEmail("operador@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("OPERADOR", "OPERATOR")));

    mockMvc
        .perform(
            post("/api/v1/itens")
                .with(authComRoles("operador@email.com", "OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":120.50,
                      "data":"2026-03-15",
                      "horarioCriacao":"2026-03-15T18:00:00",
                      "arquivosPdf":[],
                      "nomesArquivos":[],
                      "tipo":"RECEITA",
                      "tipoDocumento":"Nota fiscal",
                      "numeroDocumento":"12345",
                      "descricao":"CONTA DC"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Deve retornar 400 ao criar receita com CONTA FEFEC")
  void criarDeveRetornarBadRequestQuandoReceitaUsarContaFefec() throws Exception {
    when(usuarioRepository.findByEmail("operador@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("OPERADOR", "OPERATOR")));

    mockMvc
        .perform(
            post("/api/v1/itens")
                .with(authComRoles("operador@email.com", "OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":120.50,
                      "data":"2026-03-15",
                      "horarioCriacao":"2026-03-15T18:00:00",
                      "arquivosPdf":["ZHVtbXk="],
                      "nomesArquivos":["comprovante.pdf"],
                      "tipo":"RECEITA",
                      "tipoDocumento":"Nota fiscal",
                      "numeroDocumento":"12345",
                      "descricao":"CONTA FEFEC"
                    }
                    """))
        .andExpect(status().isBadRequest());

    verify(itemRepository, never()).save(any(Item.class));
  }

  @Test
  @DisplayName("Deve permitir criar item sem anexo quando descricao nao exigir comprovante")
  void criarDevePermitirSalvarSemAnexoQuandoDescricaoNaoExigirComprovante() throws Exception {
    Item item = new Item();
    item.setId(UUID.fromString("12121212-3434-5656-7878-909090909091"));
    item.setValor(new BigDecimal("120.50"));
    item.setData(LocalDate.of(2026, 3, 15));
    item.setHorarioCriacao(LocalDateTime.of(2026, 3, 15, 18, 0, 0));
    item.setTipo(TipoItem.DESPESA);
    item.setDescricao("OUTRAS DESPESAS");
    when(itemArquivoStorageService.salvarPdfs(
            org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(List.of());
    when(itemRepository.save(org.mockito.ArgumentMatchers.any(Item.class))).thenReturn(item);
    when(usuarioRepository.findByEmail("operador@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("OPERADOR", "OPERATOR")));

    mockMvc
        .perform(
            post("/api/v1/itens")
                .with(authComRoles("operador@email.com", "OPERATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":120.50,
                      "data":"2026-03-15",
                      "horarioCriacao":"2026-03-15T18:00:00",
                      "arquivosPdf":[],
                      "nomesArquivos":[],
                      "tipo":"DESPESA",
                      "descricao":"OUTRAS DESPESAS"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.descricao").value("OUTRAS DESPESAS"));
  }

  @Test
  @DisplayName("Deve permitir admin criar item em qualquer role")
  void criarDevePermitirAdminCriarItemEmQualquerRole() throws Exception {
    Item item = new Item();
    item.setId(UUID.fromString("12121212-3434-5656-7878-909090909090"));
    item.setCaminhoArquivoPdf("uploads/itens/item-manager.pdf");
    item.setTipo(TipoItem.DESPESA);
    item.setDescricao("SERVICOS");
    item.setRoleNome("MANAGER");
    ItemArquivo arquivoCriado = new ItemArquivo();
    arquivoCriado.setCaminhoArquivoPdf("uploads/itens/item-manager.pdf");
    arquivoCriado.setItem(item);
    item.getArquivos().add(arquivoCriado);
    when(itemArquivoStorageService.salvarPdfs(
            org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(List.of("uploads/itens/item-manager.pdf"));
    when(itemRepository.save(org.mockito.ArgumentMatchers.any(Item.class))).thenReturn(item);
    when(usuarioRepository.findByEmail("admin@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("admin", "ADMIN")));

    mockMvc
        .perform(
            post("/api/v1/itens")
                .with(authComRoles("admin@email.com", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":120.50,
                      "data":"2026-03-15",
                      "horarioCriacao":"2026-03-15T18:00:00",
                      "arquivosPdf":["cGRm"],
                      "nomesArquivos":["documento-1.pdf"],
                      "tipo":"DESPESA",
                      "role":"MANAGER",
                      "descricao":"SERVICOS"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("MANAGER"));
  }

  @Test
  @DisplayName("Deve buscar item por id")
  void buscarPorIdDeveRetornarOk() throws Exception {
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    Item item = new Item();
    item.setId(id);
    item.setValor(new BigDecimal("30.00"));
    item.setData(LocalDate.of(2026, 3, 15));
    item.setHorarioCriacao(LocalDateTime.of(2026, 3, 15, 8, 30, 0));
    item.setCaminhoArquivoPdf("uploads/itens/item-busca.pdf");
    item.setTipo(TipoItem.RECEITA);
    item.setDescricao("ALUGUEL");
    item.setRazaoSocialNome("EMPRESA TESTE");
    item.setCnpjCpf("12.345.678/0001-99");
    item.setObservacao("Observacao de teste");
    ItemArquivo arquivoBusca = new ItemArquivo();
    arquivoBusca.setCaminhoArquivoPdf("uploads/itens/item-busca.pdf");
    arquivoBusca.setItem(item);
    item.getArquivos().add(arquivoBusca);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));

    mockMvc
        .perform(get("/api/v1/itens/{id}", id).with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tipo").value("RECEITA"));
  }

  @Test
  @DisplayName("Deve retornar 404 ao buscar item inexistente por id")
  void buscarPorIdDeveRetornarNotFound() throws Exception {
    UUID id = UUID.fromString("44444444-4444-4444-4444-444444444444");
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/itens/{id}", id).with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Deve retornar 403 ao buscar item sem role em comum com criador")
  void buscarPorIdDeveRetornarForbiddenSemIntersecaoDeRoles() throws Exception {
    UUID id = UUID.fromString("99999999-1111-2222-3333-444444444444");
    Item item = new Item();
    item.setId(id);
    item.setTipo(TipoItem.RECEITA);
    item.setCriadoPor(usuarioComRoles("criador", "SUPPORT"));

    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(usuarioRepository.findByEmail("operador@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("operador", "OPERATOR")));

    mockMvc
        .perform(get("/api/v1/itens/{id}", id).with(authComRoles("operador@email.com", "OPERATOR")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Deve baixar arquivo PDF do item")
  void baixarArquivoDeveRetornarPdf() throws Exception {
    UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
    Item item = new Item();
    item.setId(id);
    item.setValor(new BigDecimal("99.99"));
    item.setData(LocalDate.of(2026, 3, 16));
    item.setHorarioCriacao(LocalDateTime.of(2026, 3, 16, 10, 0, 0));
    item.setCaminhoArquivoPdf("uploads/itens/comprovante-1.pdf");
    item.setTipo(TipoItem.DESPESA);
    item.setDescricao("IMPOSTOS");
    item.setRazaoSocialNome("EMPRESA TESTE");
    item.setCnpjCpf("12.345.678/0001-99");
    item.setObservacao("Observacao de teste");
    ItemArquivo arquivoDownload = new ItemArquivo();
    arquivoDownload.setCaminhoArquivoPdf("uploads/itens/comprovante-1.pdf");
    arquivoDownload.setItem(item);
    item.getArquivos().add(arquivoDownload);
    byte[] conteudoPdf = "pdf-teste".getBytes();

    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(itemArquivoStorageService.carregarPdf("uploads/itens/comprovante-1.pdf"))
        .thenReturn(conteudoPdf);
    when(itemArquivoStorageService.resolverNomeArquivo("uploads/itens/comprovante-1.pdf"))
        .thenReturn("comprovante-1.pdf");

    mockMvc
        .perform(
            get("/api/v1/itens/{id}/arquivo", id).with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(
            header().string("Content-Disposition", "attachment; filename=\"comprovante-1.pdf\""))
        .andExpect(content().bytes(conteudoPdf));
  }

  @Test
  @DisplayName("Deve retornar 404 no download quando item nao tem arquivo")
  void baixarArquivoDeveRetornarNotFoundQuandoItemSemArquivo() throws Exception {
    UUID id = UUID.fromString("23232323-2222-2222-2222-222222222222");
    Item item = new Item();
    item.setId(id);
    item.setCaminhoArquivoPdf(" ");
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));

    mockMvc
        .perform(
            get("/api/v1/itens/{id}/arquivo", id).with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Deve retornar 404 no download quando item nao existe")
  void baixarArquivoDeveRetornarNotFoundQuandoItemNaoExiste() throws Exception {
    UUID id = UUID.fromString("33333333-3333-3333-3333-333333333333");
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.empty());

    mockMvc
        .perform(
            get("/api/v1/itens/{id}/arquivo", id).with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Deve retornar 404 ao baixar arquivo por id inexistente")
  void baixarArquivoPorIdDeveRetornarNotFoundQuandoArquivoNaoExiste() throws Exception {
    UUID id = UUID.fromString("34343434-4444-4444-4444-444444444444");
    UUID arquivoId = UUID.fromString("45454545-5555-5555-5555-555555555555");
    Item item = new Item();
    item.setId(id);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(itemArquivoRepository.findById(arquivoId)).thenReturn(Optional.empty());

    mockMvc
        .perform(
            get("/api/v1/itens/{id}/arquivos/{arquivoId}", id, arquivoId)
                .with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Deve retornar 404 ao baixar ZIP quando item sem arquivos")
  void baixarTodosArquivosDeveRetornarNotFoundQuandoSemArquivos() throws Exception {
    UUID id = UUID.fromString("56565656-6666-6666-6666-666666666666");
    Item item = new Item();
    item.setId(id);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));

    mockMvc
        .perform(
            get("/api/v1/itens/{id}/arquivos/download", id)
                .with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Deve baixar ZIP com arquivos e observacao")
  void baixarTodosArquivosDeveRetornarZipComObservacao() throws Exception {
    UUID id = UUID.fromString("77777777-8888-9999-0000-111111111111");
    Item item = new Item();
    item.setId(id);
    item.setTipo(TipoItem.RECEITA);
    item.setObservacao(null);

    ItemArquivo arquivo = new ItemArquivo();
    arquivo.setId(UUID.fromString("12121212-1313-1414-1515-161616161616"));
    arquivo.setCaminhoArquivoPdf("uploads/itens/arquivo-teste.pdf");
    arquivo.setItem(item);
    item.getArquivos().add(arquivo);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(itemArquivoStorageService.carregarPdf("uploads/itens/arquivo-teste.pdf"))
        .thenReturn("conteudo".getBytes());
    when(itemArquivoStorageService.resolverNomeArquivo("uploads/itens/arquivo-teste.pdf"))
        .thenReturn("arquivo-teste.pdf");

    byte[] payload =
        mockMvc
            .perform(
                get("/api/v1/itens/{id}/arquivos/download", id)
                    .with(authComRoles("admin@email.com", "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        "attachment; filename=\"comprovantes-" + id + ".zip\""))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    List<String> entries = zipEntries(payload);
    assertTrue(entries.contains("arquivo-teste.pdf"));
    assertTrue(entries.contains("observacao.txt"));
  }

  @Test
  @DisplayName("Deve atualizar item")
  void atualizarDeveRetornarOk() throws Exception {
    UUID id = UUID.fromString("55555555-5555-5555-5555-555555555555");
    Item item = new Item();
    item.setId(id);
    item.setValor(new BigDecimal("10.00"));
    item.setData(LocalDate.of(2026, 3, 16));
    item.setHorarioCriacao(LocalDateTime.of(2026, 3, 16, 11, 0, 0));
    item.setCaminhoArquivoPdf("uploads/itens/antigo.pdf");
    item.setTipo(TipoItem.RECEITA);
    item.setDescricao("ALUGUEL");
    item.setRazaoSocialNome("EMPRESA TESTE");
    item.setCnpjCpf("12.345.678/0001-99");
    item.setObservacao("Observacao antiga");
    ItemArquivo arquivoAntigo = new ItemArquivo();
    arquivoAntigo.setCaminhoArquivoPdf("uploads/itens/antigo.pdf");
    arquivoAntigo.setItem(item);
    item.getArquivos().add(arquivoAntigo);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(itemArquivoStorageService.salvarPdfs(
            org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(List.of("uploads/itens/novo.pdf"));
    when(itemRepository.save(org.mockito.ArgumentMatchers.any(Item.class))).thenReturn(item);
    when(usuarioRepository.findByEmail("admin@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("admin", "ADMIN")));

    mockMvc
        .perform(
            put("/api/v1/itens/{id}", id)
                .with(authComRoles("admin@email.com", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":250.10,
                      "data":"2026-03-16",
                      "horarioCriacao":"2026-03-16T11:10:00",
                      "arquivosPdf":["cGRm"],
                      "nomesArquivos":["novo.pdf"],
                      "tipo":"DESPESA",
                      "descricao":"OUTROS",
                      "razaoSocialNome":"EMPRESA TESTE",
                      "cnpjCpf":"12.345.678/0001-99",
                      "observacao":"Observacao nova"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.caminhoArquivoPdf").value("uploads/itens/novo.pdf"));
  }

  @Test
  @DisplayName("Deve permitir admin atualizar item para qualquer role")
  void atualizarDevePermitirAdminAtualizarItemParaQualquerRole() throws Exception {
    UUID id = UUID.fromString("78787878-5656-3434-1212-000000000000");
    Item item = new Item();
    item.setId(id);
    item.setCaminhoArquivoPdf("uploads/itens/antigo.pdf");
    item.setTipo(TipoItem.RECEITA);
    item.setRoleNome("ADMIN");
    ItemArquivo arquivoAntigo = new ItemArquivo();
    arquivoAntigo.setCaminhoArquivoPdf("uploads/itens/antigo.pdf");
    arquivoAntigo.setItem(item);
    item.getArquivos().add(arquivoAntigo);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(itemArquivoStorageService.salvarPdfs(
            org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(List.of("uploads/itens/novo.pdf"));
    when(itemRepository.save(org.mockito.ArgumentMatchers.any(Item.class)))
        .thenAnswer(
            invocation -> {
              Item atualizado = invocation.getArgument(0);
              atualizado.setRoleNome("MANAGER");
              return atualizado;
            });
    when(usuarioRepository.findByEmail("admin@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("admin", "ADMIN")));

    mockMvc
        .perform(
            put("/api/v1/itens/{id}", id)
                .with(authComRoles("admin@email.com", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":250.10,
                      "data":"2026-03-16",
                      "horarioCriacao":"2026-03-16T11:10:00",
                      "arquivosPdf":["cGRm"],
                      "nomesArquivos":["novo.pdf"],
                      "tipo":"DESPESA",
                      "role":"MANAGER",
                      "descricao":"OUTROS"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("MANAGER"));
  }

  @Test
  @DisplayName("Deve retornar 400 ao atualizar despesa que excede limite de categoria")
  void atualizarDeveRetornarBadRequestQuandoDespesaExcederLimiteDeCategoria() throws Exception {
    UUID id = UUID.fromString("98989898-5656-3434-1212-000000000000");
    Item item = new Item();
    item.setId(id);
    item.setRoleNome("ADMIN");
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(usuarioRepository.findByEmail("admin@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("admin", "ADMIN")));
    doThrow(
            new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Nao e permitido adicionar esta despesa. Locacao pode representar no maximo 20% do total de despesas."))
        .when(itemExpenseLimitService)
        .validarLimiteDespesa(any(), eq("ADMIN"), eq(id));

    mockMvc
        .perform(
            put("/api/v1/itens/{id}", id)
                .with(authComRoles("admin@email.com", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":250.10,
                      "data":"2026-03-16",
                      "horarioCriacao":"2026-03-16T11:10:00",
                      "arquivosPdf":["cGRm"],
                      "nomesArquivos":["novo.pdf"],
                      "tipo":"DESPESA",
                      "descricao":"ALUGUEL DE VEÍCULOS"
                    }
                    """))
        .andExpect(status().isBadRequest());

    verify(itemRepository, never()).save(any(Item.class));
  }

  @Test
  @DisplayName("Deve retornar 404 ao atualizar item inexistente")
  void atualizarDeveRetornarNotFound() throws Exception {
    UUID id = UUID.fromString("66666666-6666-6666-6666-666666666666");
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.empty());

    mockMvc
        .perform(
            put("/api/v1/itens/{id}", id)
                .with(authComRoles("admin@email.com", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":250.10,
                      "data":"2026-03-16",
                      "horarioCriacao":"2026-03-16T11:10:00",
                      "arquivosPdf":["cGRm"],
                      "nomesArquivos":["novo.pdf"],
                      "tipo":"DESPESA",
                      "descricao":"OUTROS",
                      "razaoSocialNome":"EMPRESA TESTE",
                      "cnpjCpf":"12.345.678/0001-99",
                      "observacao":"Observacao nova"
                    }
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Deve retornar 400 ao atualizar item para CPF que ja existe em outro item")
  void atualizarDeveRetornarBadRequestQuandoCpfJaExistirEmOutroItem() throws Exception {
    UUID id = UUID.fromString("12121212-1212-1212-1212-121212121212");
    Item item = new Item();
    item.setId(id);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(usuarioRepository.findByEmail("admin@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("admin", "ADMIN")));
    when(itemRepository.countByDocumentoNormalizadoAndIdNot("12345678900", id)).thenReturn(1L);

    mockMvc
        .perform(
            put("/api/v1/itens/{id}", id)
                .with(authComRoles("admin@email.com", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":250.10,
                      "data":"2026-03-16",
                      "horarioCriacao":"2026-03-16T11:10:00",
                      "arquivosPdf":["cGRm"],
                      "nomesArquivos":["novo.pdf"],
                      "tipo":"DESPESA",
                      "descricao":"OUTROS",
                      "cnpjCpf":"123.456.789-00"
                    }
                    """))
        .andExpect(status().isBadRequest());

    verify(itemRepository, never()).save(any(Item.class));
  }

  @Test
  @DisplayName("Deve retornar 400 ao atualizar conta financeira sem anexo")
  void atualizarDeveRetornarBadRequestQuandoContaFinanceiraNaoTiverAnexo() throws Exception {
    UUID id = UUID.fromString("12121212-5656-7878-9090-000000000001");
    Item item = new Item();
    item.setId(id);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(usuarioRepository.findByEmail("admin@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("admin", "ADMIN")));

    mockMvc
        .perform(
            put("/api/v1/itens/{id}", id)
                .with(authComRoles("admin@email.com", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":250.10,
                      "data":"2026-03-16",
                      "horarioCriacao":"2026-03-16T11:10:00",
                      "arquivosPdf":[],
                      "nomesArquivos":[],
                      "tipo":"RECEITA",
                      "descricao":"CONTA FEFC"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Deve permitir atualizar item sem anexo quando descricao nao exigir comprovante")
  void atualizarDevePermitirSalvarSemAnexoQuandoDescricaoNaoExigirComprovante() throws Exception {
    UUID id = UUID.fromString("12121212-5656-7878-9090-000000000002");
    Item item = new Item();
    item.setId(id);
    item.setTipo(TipoItem.DESPESA);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(itemArquivoStorageService.salvarPdfs(
            org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(List.of());
    when(itemRepository.save(org.mockito.ArgumentMatchers.any(Item.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(usuarioRepository.findByEmail("admin@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("admin", "ADMIN")));

    mockMvc
        .perform(
            put("/api/v1/itens/{id}", id)
                .with(authComRoles("admin@email.com", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "valor":250.10,
                      "data":"2026-03-16",
                      "horarioCriacao":"2026-03-16T11:10:00",
                      "arquivosPdf":[],
                      "nomesArquivos":[],
                      "tipo":"DESPESA",
                      "descricao":"OUTRAS DESPESAS"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.descricao").value("OUTRAS DESPESAS"));

    verify(notificacaoService).sincronizarComItem(any(Item.class));
  }

  @Test
  @DisplayName("Deve deletar item")
  void deletarDeveRetornarNoContent() throws Exception {
    UUID id = UUID.fromString("77777777-7777-7777-7777-777777777777");
    Item item = new Item();
    item.setId(id);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));

    mockMvc
        .perform(delete("/api/v1/itens/{id}", id).with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isNoContent());

    verify(notificacaoService).removerPorItemId(id);
  }

  @Test
  @DisplayName("Deve proibir usuario CONTABIL de deletar item")
  void deletarDeveRetornarForbiddenQuandoUsuarioForContabil() throws Exception {
    UUID id = UUID.fromString("67676767-7777-7777-7777-777777777777");

    mockMvc
        .perform(
            delete("/api/v1/itens/{id}", id).with(authComRoles("contabil@email.com", "CONTABIL")))
        .andExpect(status().isForbidden());

    verify(itemRepository, never()).delete(any(Item.class));
    verify(notificacaoService, never()).removerPorItemId(any(UUID.class));
  }

  @Test
  @DisplayName("Deve proibir deletar item verificado")
  void deletarDeveRetornarConflictQuandoItemEstiverVerificado() throws Exception {
    UUID id = UUID.fromString("68686868-7777-7777-7777-777777777777");
    Item item = new Item();
    item.setId(id);
    item.setVerificado(true);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));

    mockMvc
        .perform(delete("/api/v1/itens/{id}", id).with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isConflict());

    verify(itemRepository, never()).delete(any(Item.class));
    verify(notificacaoService, never()).removerPorItemId(any(UUID.class));
  }

  @Test
  @DisplayName("Deve retornar 404 ao deletar item inexistente")
  void deletarDeveRetornarNotFound() throws Exception {
    UUID id = UUID.fromString("88888888-8888-8888-8888-888888888888");
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.empty());

    mockMvc
        .perform(delete("/api/v1/itens/{id}", id).with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isNotFound());
  }

  private RequestPostProcessor authComRoles(String email, String... roles) {
    return request -> {
      List<SimpleGrantedAuthority> authorities = new ArrayList<>();
      for (String role : roles) {
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
      }
      request.setUserPrincipal(
          new UsernamePasswordAuthenticationToken(email, "senha", authorities));
      return request;
    };
  }

  private Usuario usuarioComRoles(String nome, String... roleNomes) {
    Usuario usuario = new Usuario();
    usuario.setNome(nome);
    usuario.setEmail(nome.toLowerCase() + "@email.com");
    for (String roleNome : roleNomes) {
      Role role = new Role();
      role.setNome(roleNome);
      usuario.getRoles().add(role);
    }
    return usuario;
  }

  @Test
  @DisplayName("Deve listar arquivos do item")
  void listarArquivosDeveRetornarOk() throws Exception {
    UUID id = UUID.fromString("99999999-2222-3333-4444-555555555555");
    Item item = new Item();
    item.setId(id);
    item.setTipo(TipoItem.RECEITA);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));

    ItemArquivo arquivo = new ItemArquivo();
    arquivo.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    arquivo.setCaminhoArquivoPdf("uploads/itens/extra.pdf");
    arquivo.setItem(item);
    item.getArquivos().add(arquivo);

    mockMvc
        .perform(
            get("/api/v1/itens/{id}/arquivos", id).with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].caminhoArquivoPdf").value("uploads/itens/extra.pdf"));
  }

  @Test
  @DisplayName("Deve adicionar arquivos ao item")
  void adicionarArquivosDeveRetornarOk() throws Exception {
    UUID id = UUID.fromString("99999999-aaaa-bbbb-cccc-111111111111");
    Item item = new Item();
    item.setId(id);
    item.setTipo(TipoItem.RECEITA);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(itemArquivoStorageService.salvarPdfs(
            org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(List.of("uploads/itens/novo-1.pdf", "uploads/itens/novo-2.pdf"));
    when(itemRepository.save(org.mockito.ArgumentMatchers.any(Item.class))).thenReturn(item);

    mockMvc
        .perform(
            post("/api/v1/itens/{id}/arquivos", id)
                .with(authComRoles("admin@email.com", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "arquivosPdf":["cGRm","cGRm"],
                      "nomesArquivos":["extra-1.pdf","extra-2.pdf"]
                    }
                    """))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Deve atualizar observacao via PATCH sem alterar arquivos")
  void atualizarObservacaoDeveRetornarOk() throws Exception {
    UUID id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    Item item = new Item();
    item.setId(id);
    item.setTipo(TipoItem.RECEITA);
    item.setObservacao("Antes");
    ItemArquivo arquivo = new ItemArquivo();
    arquivo.setCaminhoArquivoPdf("uploads/itens/keep.pdf");
    arquivo.setItem(item);
    item.getArquivos().add(arquivo);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(itemRepository.save(org.mockito.ArgumentMatchers.any(Item.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    mockMvc
        .perform(
            patch("/api/v1/itens/{id}/observacao", id)
                .with(authComRoles("admin@email.com", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"observacao":"Nova observacao"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.observacao").value("Nova observacao"));

    assertEquals(1, item.getArquivos().size());
    assertEquals("uploads/itens/keep.pdf", item.getArquivos().getFirst().getCaminhoArquivoPdf());
  }

  @Test
  @DisplayName("Deve atualizar verificacao via PATCH")
  void atualizarVerificacaoDeveRetornarOk() throws Exception {
    UUID id = UUID.fromString("abababab-bbbb-cccc-dddd-eeeeeeeeeeee");
    Item item = new Item();
    item.setId(id);
    item.setTipo(TipoItem.RECEITA);
    item.setVerificado(false);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(itemRepository.save(org.mockito.ArgumentMatchers.any(Item.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    mockMvc
        .perform(
            patch("/api/v1/itens/{id}/verificacao", id)
                .with(authComRoles("admin@email.com", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"verificado":true}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verificado").value(true));

    assertTrue(item.isVerificado());
  }

  @Test
  @DisplayName("Deve permitir SUPPORT marcar comprovante como verificado")
  void atualizarVerificacaoDevePermitirSupportMarcarComoVerificado() throws Exception {
    UUID id = UUID.fromString("abababab-bbbb-cccc-dddd-eeeeeeeeeeef");
    Item item = new Item();
    item.setId(id);
    item.setTipo(TipoItem.RECEITA);
    item.setRoleNome("SUPPORT");
    item.setVerificado(false);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(usuarioRepository.findByEmail("support@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("support", "SUPPORT")));
    when(itemRepository.save(org.mockito.ArgumentMatchers.any(Item.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    mockMvc
        .perform(
            patch("/api/v1/itens/{id}/verificacao", id)
                .with(authComRoles("support@email.com", "SUPPORT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"verificado":true}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verificado").value(true));

    assertTrue(item.isVerificado());
  }

  @Test
  @DisplayName("Deve proibir SUPPORT desmarcar comprovante verificado")
  void atualizarVerificacaoDeveProibirSupportDesmarcarVerificado() throws Exception {
    UUID id = UUID.fromString("abababab-bbbb-cccc-dddd-eeeeeeeeeef0");
    Item item = new Item();
    item.setId(id);
    item.setTipo(TipoItem.RECEITA);
    item.setRoleNome("SUPPORT");
    item.setVerificado(true);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(usuarioRepository.findByEmail("support@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("support", "SUPPORT")));

    mockMvc
        .perform(
            patch("/api/v1/itens/{id}/verificacao", id)
                .with(authComRoles("support@email.com", "SUPPORT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"verificado":false}
                    """))
        .andExpect(status().isForbidden());

    assertTrue(item.isVerificado());
    verify(itemRepository, never()).save(any(Item.class));
  }

  @Test
  @DisplayName("Deve proibir CANDIDATO marcar comprovante como verificado")
  void atualizarVerificacaoDeveProibirCandidatoMarcarComoVerificado() throws Exception {
    UUID id = UUID.fromString("abababab-bbbb-cccc-dddd-eeeeeeeeeef1");
    Item item = new Item();
    item.setId(id);
    item.setTipo(TipoItem.RECEITA);
    item.setRoleNome("CANDIDATO");
    item.setVerificado(false);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(usuarioRepository.findByEmail("candidato@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("candidato", "CANDIDATO")));

    mockMvc
        .perform(
            patch("/api/v1/itens/{id}/verificacao", id)
                .with(authComRoles("candidato@email.com", "CANDIDATO"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"verificado":true}
                    """))
        .andExpect(status().isForbidden());

    assertFalse(item.isVerificado());
    verify(itemRepository, never()).save(any(Item.class));
  }

  @Test
  @DisplayName("Deve proibir CANDIDATO desmarcar comprovante verificado")
  void atualizarVerificacaoDeveProibirCandidatoDesmarcarVerificado() throws Exception {
    UUID id = UUID.fromString("abababab-bbbb-cccc-dddd-eeeeeeeeeef2");
    Item item = new Item();
    item.setId(id);
    item.setTipo(TipoItem.RECEITA);
    item.setRoleNome("CANDIDATO");
    item.setVerificado(true);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));
    when(usuarioRepository.findByEmail("candidato@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("candidato", "CANDIDATO")));

    mockMvc
        .perform(
            patch("/api/v1/itens/{id}/verificacao", id)
                .with(authComRoles("candidato@email.com", "CANDIDATO"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"verificado":false}
                    """))
        .andExpect(status().isForbidden());

    assertTrue(item.isVerificado());
    verify(itemRepository, never()).save(any(Item.class));
  }

  @Test
  @DisplayName("Deve retornar 400 ao adicionar arquivos sem PDFs")
  void adicionarArquivosDeveRetornarBadRequestQuandoSemArquivos() throws Exception {
    UUID id = UUID.fromString("99999999-aaaa-bbbb-cccc-222222222222");
    Item item = new Item();
    item.setId(id);
    item.setTipo(TipoItem.RECEITA);
    when(itemRepository.findByIdComCriadorERoles(id)).thenReturn(Optional.of(item));

    mockMvc
        .perform(
            post("/api/v1/itens/{id}/arquivos", id)
                .with(authComRoles("admin@email.com", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "arquivosPdf":[]
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Deve deletar arquivo do item")
  void deletarArquivoDeveRetornarNoContent() throws Exception {
    UUID itemId = UUID.fromString("22222222-3333-4444-5555-666666666666");
    UUID arquivoId = UUID.fromString("99999999-8888-7777-6666-555555555555");
    Item item = new Item();
    item.setId(itemId);
    item.setTipo(TipoItem.RECEITA);
    item.setCaminhoArquivoPdf("uploads/itens/keep.pdf");
    ItemArquivo arquivo = new ItemArquivo();
    arquivo.setId(arquivoId);
    arquivo.setCaminhoArquivoPdf("uploads/itens/keep.pdf");
    arquivo.setItem(item);
    item.getArquivos().add(arquivo);

    when(itemRepository.findByIdComCriadorERoles(itemId)).thenReturn(Optional.of(item));
    when(itemRepository.save(org.mockito.ArgumentMatchers.any(Item.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    mockMvc
        .perform(
            delete("/api/v1/itens/{id}/arquivos/{arquivoId}", itemId, arquivoId)
                .with(authComRoles("admin@email.com", "ADMIN")))
        .andExpect(status().isNoContent());

    assertTrue(item.getArquivos().isEmpty());
    assertNull(item.getCaminhoArquivoPdf());
  }

  private List<String> zipEntries(byte[] payload) throws Exception {
    List<String> entries = new ArrayList<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(payload))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        entries.add(entry.getName());
      }
    }
    return entries;
  }
}
