package com.sistema_contabilidade.relatorio.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroPdfData;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResponse;
import com.sistema_contabilidade.relatorio.dto.RelatorioItemDto;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("RelatorioFinanceiroService unit tests")
class RelatorioFinanceiroServiceTest {

  @Test
  @DisplayName("Deve incluir descricao do item no DTO do relatorio")
  void deveIncluirDescricaoNoDto() {
    ItemRepository itemRepository = Mockito.mock(ItemRepository.class);
    RoleRepository roleRepository = Mockito.mock(RoleRepository.class);
    UsuarioRepository usuarioRepository = Mockito.mock(UsuarioRepository.class);
    PlaywrightPdfService playwrightPdfService = Mockito.mock(PlaywrightPdfService.class);
    RelatorioFinanceiroService service =
        new RelatorioFinanceiroService(
            itemRepository, roleRepository, usuarioRepository, playwrightPdfService);

    RelatorioItemDto item =
        dto("ENERGIA", TipoItem.RECEITA, "10.00", LocalDate.of(2026, 3, 31), 10, 0);

    when(itemRepository.findAllRelatorioItensOrderByDataDescHorarioCriacaoDesc())
        .thenReturn(List.of(item));

    RelatorioFinanceiroResponse response = service.gerar(adminAuth(), null);

    assertEquals(1, response.receitas().size());
    assertEquals("ENERGIA", response.receitas().getFirst().descricao());
    assertEquals(BigDecimal.ZERO, response.receitasFinanceiras());
    assertEquals(BigDecimal.ZERO, response.receitasEstimaveis());
    assertEquals(BigDecimal.ZERO, response.despesasConsideradas());
    assertEquals(BigDecimal.ZERO, response.despesasAdvocaciaContabilidade());
    assertEquals(BigDecimal.ZERO, response.totalDespesas());
    assertEquals(BigDecimal.ZERO, response.limiteGastosCombustivelPercentual());
    assertEquals(BigDecimal.ZERO, response.limiteGastosAlimentacaoPercentual());
    assertEquals(BigDecimal.ZERO, response.limiteGastosLocacaoPercentual());
  }

  @Test
  @DisplayName("Deve permitir filtro por role propria para usuario com multiplas roles")
  void devePermitirFiltroPorRolePropriaParaUsuarioComMultiplasRoles() {
    ItemRepository itemRepository = Mockito.mock(ItemRepository.class);
    RelatorioFinanceiroService service =
        new RelatorioFinanceiroService(
            itemRepository,
            Mockito.mock(RoleRepository.class),
            Mockito.mock(UsuarioRepository.class),
            Mockito.mock(PlaywrightPdfService.class));
    RelatorioItemDto receita =
        dto("SERVICOS", TipoItem.RECEITA, "50.00", LocalDate.of(2026, 4, 1), 9, 0);

    when(itemRepository.findRelatorioItensByRoleNomeOrderByDataDescHorarioCriacaoDesc("FINANCEIRO"))
        .thenReturn(List.of(receita));

    RelatorioFinanceiroResponse response =
        service.gerar(
            authentication("user@email.com", "ROLE_FINANCEIRO", "ROLE_OPERADOR"), "financeiro");

    verify(itemRepository)
        .findRelatorioItensByRoleNomeOrderByDataDescHorarioCriacaoDesc("FINANCEIRO");
    assertEquals(BigDecimal.ZERO, response.receitasFinanceiras());
    assertEquals(BigDecimal.ZERO, response.receitasEstimaveis());
    assertEquals(BigDecimal.ZERO, response.despesasConsideradas());
    assertEquals(BigDecimal.ZERO, response.despesasAdvocaciaContabilidade());
    assertEquals(new BigDecimal("50.00"), response.totalReceitas());
  }

  @Test
  @DisplayName("Deve proibir filtro por role que nao pertence ao usuario")
  void deveProibirFiltroPorRoleQueNaoPertenceAoUsuario() {
    RelatorioFinanceiroService service = service();
    UsernamePasswordAuthenticationToken authentication =
        authentication("user@email.com", "ROLE_MANAGER", "ROLE_OPERADOR");

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> service.gerar(authentication, "ADMIN"));

    assertEquals(403, exception.getStatusCode().value());
  }

  @Test
  @DisplayName("Deve listar roles disponiveis para admin ordenadas")
  void deveListarRolesDisponiveisParaAdminOrdenadas() {
    RoleRepository roleRepository = Mockito.mock(RoleRepository.class);
    RelatorioFinanceiroService service =
        new RelatorioFinanceiroService(
            Mockito.mock(ItemRepository.class),
            roleRepository,
            Mockito.mock(UsuarioRepository.class),
            Mockito.mock(PlaywrightPdfService.class));
    Role admin = new Role();
    admin.setNome("admin");
    Role financeiro = new Role();
    financeiro.setNome(" financeiro ");

    when(roleRepository.findAllRoleNamesOrdered())
        .thenReturn(List.of("ADMIN", "CANDIDATO", "CONTABIL", "FINANCEIRO", "MANAGER", "SUPPORT"));

    List<String> roles = service.listarRolesDisponiveis(adminAuth());

    assertEquals(List.of("FINANCEIRO"), roles);
  }

  @Test
  @DisplayName("Deve listar roles do proprio usuario quando nao for admin")
  void deveListarRolesDoProprioUsuarioQuandoNaoForAdmin() {
    RelatorioFinanceiroService service = service();
    UsernamePasswordAuthenticationToken authentication =
        authentication("user@email.com", "ROLE_OPERADOR", "ROLE_FINANCEIRO", "ROLE_SUPPORT");

    List<String> roles = service.listarRolesDisponiveis(authentication);

    assertEquals(List.of("FINANCEIRO", "OPERADOR"), roles);
  }

  @Test
  @DisplayName("Deve aplicar filtro por role e usar repositorio especifico")
  void deveAplicarFiltroPorRoleEUsarRepositorioEspecifico() {
    ItemRepository itemRepository = Mockito.mock(ItemRepository.class);
    RelatorioFinanceiroService service =
        new RelatorioFinanceiroService(
            itemRepository,
            Mockito.mock(RoleRepository.class),
            Mockito.mock(UsuarioRepository.class),
            Mockito.mock(PlaywrightPdfService.class));
    RelatorioItemDto receita =
        dto("SERVICOS", TipoItem.RECEITA, "50.00", LocalDate.of(2026, 4, 1), 9, 0);

    when(itemRepository.findRelatorioItensByRoleNomeOrderByDataDescHorarioCriacaoDesc("FINANCEIRO"))
        .thenReturn(List.of(receita));

    RelatorioFinanceiroResponse response = service.gerar(adminAuth(), "financeiro");

    verify(itemRepository)
        .findRelatorioItensByRoleNomeOrderByDataDescHorarioCriacaoDesc("FINANCEIRO");
    assertEquals(BigDecimal.ZERO, response.receitasFinanceiras());
    assertEquals(BigDecimal.ZERO, response.receitasEstimaveis());
    assertEquals(BigDecimal.ZERO, response.despesasConsideradas());
    assertEquals(BigDecimal.ZERO, response.despesasAdvocaciaContabilidade());
    assertEquals(new BigDecimal("50.00"), response.totalReceitas());
  }

  @Test
  @DisplayName("Deve classificar receitas financeiras e estimaveis no resumo")
  void deveClassificarReceitasFinanceirasEEstimaveisNoResumo() {
    ItemRepository itemRepository = Mockito.mock(ItemRepository.class);
    RelatorioFinanceiroService service =
        new RelatorioFinanceiroService(
            itemRepository,
            Mockito.mock(RoleRepository.class),
            Mockito.mock(UsuarioRepository.class),
            Mockito.mock(PlaywrightPdfService.class));

    when(itemRepository.findAllRelatorioItensOrderByDataDescHorarioCriacaoDesc())
        .thenReturn(
            List.of(
                dto("CONTA FEFC", TipoItem.RECEITA, "100.00", LocalDate.of(2026, 4, 1), 8, 0),
                dto("CONTA FP", TipoItem.RECEITA, "50.00", LocalDate.of(2026, 4, 2), 8, 0),
                dto("ESTIMÁVEL", TipoItem.RECEITA, "25.00", LocalDate.of(2026, 4, 3), 8, 0),
                dto("OUTRAS RECEITAS", TipoItem.RECEITA, "10.00", LocalDate.of(2026, 4, 4), 8, 0),
                dto("SERVICOS", TipoItem.DESPESA, "40.00", LocalDate.of(2026, 4, 5), 8, 0)));

    RelatorioFinanceiroResponse response = service.gerar(adminAuth(), null);

    assertEquals(new BigDecimal("150.00"), response.receitasFinanceiras());
    assertEquals(new BigDecimal("25.00"), response.receitasEstimaveis());
    assertEquals(new BigDecimal("185.00"), response.totalReceitas());
    assertEquals(new BigDecimal("40.00"), response.totalDespesas());
    assertEquals(new BigDecimal("145.00"), response.saldoFinal());
  }

  @Test
  @DisplayName("Deve separar despesas consideradas de advocacia e contabilidade")
  void deveSepararDespesasConsideradasDeAdvocaciaEContabilidade() {
    ItemRepository itemRepository = Mockito.mock(ItemRepository.class);
    RelatorioFinanceiroService service =
        new RelatorioFinanceiroService(
            itemRepository,
            Mockito.mock(RoleRepository.class),
            Mockito.mock(UsuarioRepository.class),
            Mockito.mock(PlaywrightPdfService.class));

    when(itemRepository.findAllRelatorioItensOrderByDataDescHorarioCriacaoDesc())
        .thenReturn(
            List.of(
                dto(
                    "SERVIÇOS ADVOCATÍCIOS",
                    TipoItem.DESPESA,
                    "30.00",
                    LocalDate.of(2026, 4, 1),
                    8,
                    0),
                dto(
                    "SERVIÇOS CONTÁBEIS",
                    TipoItem.DESPESA,
                    "20.00",
                    LocalDate.of(2026, 4, 2),
                    8,
                    0),
                dto("Internet", TipoItem.DESPESA, "50.00", LocalDate.of(2026, 4, 3), 8, 0),
                dto("CONTA DC", TipoItem.RECEITA, "200.00", LocalDate.of(2026, 4, 4), 8, 0)));

    RelatorioFinanceiroResponse response = service.gerar(adminAuth(), null);

    assertEquals(new BigDecimal("50.00"), response.despesasAdvocaciaContabilidade());
    assertEquals(new BigDecimal("50.00"), response.despesasConsideradas());
    assertEquals(new BigDecimal("100.00"), response.totalDespesas());
  }

  @Test
  @DisplayName("Deve calcular limites de gastos como percentual do total de despesas")
  void deveCalcularLimitesDeGastosComoPercentualDoTotalDeDespesas() {
    ItemRepository itemRepository = Mockito.mock(ItemRepository.class);
    RelatorioFinanceiroService service =
        new RelatorioFinanceiroService(
            itemRepository,
            Mockito.mock(RoleRepository.class),
            Mockito.mock(UsuarioRepository.class),
            Mockito.mock(PlaywrightPdfService.class));

    when(itemRepository.findAllRelatorioItensOrderByDataDescHorarioCriacaoDesc())
        .thenReturn(
            List.of(
                dto(
                    "COMBUSTÍVEIS E LUBRIFICANTES",
                    TipoItem.DESPESA,
                    "100000.00",
                    LocalDate.of(2026, 4, 1),
                    8,
                    0),
                dto("ALIMENTAÇÃO", TipoItem.DESPESA, "75000.00", LocalDate.of(2026, 4, 2), 8, 0),
                dto(
                    "ALUGUEL DE IMÓVEIS",
                    TipoItem.DESPESA,
                    "125000.00",
                    LocalDate.of(2026, 4, 3),
                    8,
                    0),
                dto(
                    "ALUGUEL DE VEÍCULOS",
                    TipoItem.DESPESA,
                    "50000.00",
                    LocalDate.of(2026, 4, 4),
                    8,
                    0),
                dto("INTERNET", TipoItem.DESPESA, "150000.00", LocalDate.of(2026, 4, 5), 8, 0)));

    RelatorioFinanceiroResponse response = service.gerar(adminAuth(), null);

    assertEquals(new BigDecimal("500000.00"), response.totalDespesas());
    assertEquals(new BigDecimal("0.2000"), response.limiteGastosCombustivelPercentual());
    assertEquals(new BigDecimal("0.1500"), response.limiteGastosAlimentacaoPercentual());
    assertEquals(new BigDecimal("0.1000"), response.limiteGastosLocacaoPercentual());
  }

  @Test
  @DisplayName("Deve montar dados do PDF com categorias ordenadas e cores estaveis")
  void deveMontarDadosDoPdfComCategoriasOrdenadasECoresEstaveis() {
    ItemRepository itemRepository = Mockito.mock(ItemRepository.class);
    RoleRepository roleRepository = Mockito.mock(RoleRepository.class);
    UsuarioRepository usuarioRepository = Mockito.mock(UsuarioRepository.class);
    PlaywrightPdfService playwrightPdfService = Mockito.mock(PlaywrightPdfService.class);
    RelatorioFinanceiroService service =
        new RelatorioFinanceiroService(
            itemRepository, roleRepository, usuarioRepository, playwrightPdfService);
    RelatorioFinanceiroResponse relatorio =
        new RelatorioFinanceiroResponse(
            new BigDecimal("1000.00"),
            BigDecimal.ZERO,
            new BigDecimal("1000.00"),
            new BigDecimal("250.00"),
            new BigDecimal("50.00"),
            new BigDecimal("300.00"),
            new BigDecimal("0.0000"),
            new BigDecimal("0.6667"),
            new BigDecimal("0.0000"),
            new BigDecimal("700.00"),
            List.of(dto("SERVICOS", TipoItem.RECEITA, "1000.00", LocalDate.of(2026, 4, 1), 8, 0)),
            List.of(
                dto("Alimentação", TipoItem.DESPESA, "200.00", LocalDate.of(2026, 4, 2), 9, 0),
                dto("Telefone", TipoItem.DESPESA, "70.00", LocalDate.of(2026, 4, 3), 10, 0),
                dto(
                    "Categoria Livre",
                    TipoItem.DESPESA,
                    "30.00",
                    LocalDate.of(2026, 4, 4),
                    11,
                    0)));
    Usuario usuario = new Usuario();
    usuario.setNome("Maria Silva");
    byte[] pdf = "pdf".getBytes(StandardCharsets.UTF_8);
    ArgumentCaptor<RelatorioFinanceiroPdfData> captor =
        ArgumentCaptor.forClass(RelatorioFinanceiroPdfData.class);

    when(usuarioRepository.findByEmail("maria@email.com")).thenReturn(Optional.of(usuario));
    when(playwrightPdfService.generateFinancialReportPdf(captor.capture())).thenReturn(pdf);

    byte[] result = service.gerarPdf(authentication("maria@email.com", "ROLE_ADMIN"), relatorio);

    RelatorioFinanceiroPdfData data = captor.getValue();
    assertArrayEquals(pdf, result);
    assertEquals("Maria Silva", data.responsavel());
    assertEquals("01/04/2026 a 04/04/2026", data.periodo());
    assertTrue(data.resultadoDescricao().contains("saldo positivo"));
    assertEquals(3, data.categoriasDespesa().size());
    assertEquals("Alimentação", data.categoriasDespesa().getFirst().nome());
    assertEquals("R$ 200,00", data.categoriasDespesa().getFirst().valor());
    assertEquals("66,67%", data.categoriasDespesa().getFirst().percentual());
    assertEquals("#A65628", data.categoriasDespesa().getFirst().cor());
    assertEquals("Telefone", data.categoriasDespesa().get(1).nome());
    assertEquals("#DCBEFF", data.categoriasDespesa().get(1).cor());
    assertEquals("Categoria Livre", data.categoriasDespesa().get(2).nome());
    assertFalse(data.donutSlices().isEmpty());
  }

  @Test
  @DisplayName("Deve usar fallbacks no PDF quando relatorio vier vazio")
  void deveUsarFallbacksNoPdfQuandoRelatorioVierVazio() {
    UsuarioRepository usuarioRepository = Mockito.mock(UsuarioRepository.class);
    PlaywrightPdfService playwrightPdfService = Mockito.mock(PlaywrightPdfService.class);
    RelatorioFinanceiroService service =
        new RelatorioFinanceiroService(
            Mockito.mock(ItemRepository.class),
            Mockito.mock(RoleRepository.class),
            usuarioRepository,
            playwrightPdfService);
    RelatorioFinanceiroResponse relatorio =
        new RelatorioFinanceiroResponse(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            List.of(),
            List.of());
    byte[] pdf = "pdf".getBytes(StandardCharsets.UTF_8);
    ArgumentCaptor<RelatorioFinanceiroPdfData> captor =
        ArgumentCaptor.forClass(RelatorioFinanceiroPdfData.class);

    when(usuarioRepository.findByEmail("sem-nome@email.com")).thenReturn(Optional.empty());
    when(playwrightPdfService.generateFinancialReportPdf(captor.capture())).thenReturn(pdf);

    service.gerarPdf(authentication("sem-nome@email.com", "ROLE_ADMIN"), relatorio);

    RelatorioFinanceiroPdfData data = captor.getValue();
    assertEquals("sem-nome@email.com", data.responsavel());
    assertEquals("Sem periodo definido", data.periodo());
    assertEquals(
        "O periodo fechou em equilibrio entre entradas e saidas.", data.resultadoDescricao());
    assertEquals("Sem receitas registradas", data.receitas().getFirst().descricao());
    assertEquals("Sem despesas registradas", data.despesas().getFirst().descricao());
    assertTrue(data.categoriasDespesa().isEmpty());
    assertTrue(data.donutSlices().isEmpty());
  }

  @Test
  @DisplayName("Deve usar descricao padrao e saldo negativo no PDF")
  void deveUsarDescricaoPadraoESaldoNegativoNoPdf() {
    UsuarioRepository usuarioRepository = Mockito.mock(UsuarioRepository.class);
    PlaywrightPdfService playwrightPdfService = Mockito.mock(PlaywrightPdfService.class);
    RelatorioFinanceiroService service =
        new RelatorioFinanceiroService(
            Mockito.mock(ItemRepository.class),
            Mockito.mock(RoleRepository.class),
            usuarioRepository,
            playwrightPdfService);
    RelatorioFinanceiroResponse relatorio =
        new RelatorioFinanceiroResponse(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("25.00"),
            new BigDecimal("25.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("-25.00"),
            List.of(),
            List.of(dto(null, TipoItem.DESPESA, "25.00", null, null, null)));
    ArgumentCaptor<RelatorioFinanceiroPdfData> captor =
        ArgumentCaptor.forClass(RelatorioFinanceiroPdfData.class);

    when(usuarioRepository.findByEmail("maria@email.com")).thenReturn(Optional.of(new Usuario()));
    when(playwrightPdfService.generateFinancialReportPdf(captor.capture()))
        .thenReturn("pdf".getBytes(StandardCharsets.UTF_8));

    service.gerarPdf(authentication("maria@email.com", "ROLE_ADMIN"), relatorio);

    RelatorioFinanceiroPdfData data = captor.getValue();
    assertTrue(data.resultadoDescricao().contains("saldo negativo"));
    assertEquals("Despesa registrada no periodo", data.despesas().getFirst().descricao());
    assertEquals("Despesa sem descricao", data.despesas().getFirst().categoria());
  }

  private RelatorioFinanceiroService service() {
    return new RelatorioFinanceiroService(
        Mockito.mock(ItemRepository.class),
        Mockito.mock(RoleRepository.class),
        Mockito.mock(UsuarioRepository.class),
        Mockito.mock(PlaywrightPdfService.class));
  }

  private UsernamePasswordAuthenticationToken adminAuth() {
    return authentication("admin@email.com", "ROLE_ADMIN");
  }

  private UsernamePasswordAuthenticationToken authentication(String email, String... roles) {
    return new UsernamePasswordAuthenticationToken(
        email, "senha", java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
  }

  private RelatorioItemDto dto(
      String descricao, TipoItem tipo, String valor, LocalDate data, Integer hour, Integer minute) {
    LocalDateTime horario =
        hour == null || minute == null || data == null
            ? null
            : LocalDateTime.of(data, java.time.LocalTime.of(hour, minute));
    return new RelatorioItemDto(
        java.util.UUID.randomUUID(), tipo, new BigDecimal(valor), data, horario, descricao);
  }
}
