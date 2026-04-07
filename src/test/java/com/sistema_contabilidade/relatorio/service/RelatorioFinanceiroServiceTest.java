package com.sistema_contabilidade.relatorio.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroPdfData;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResponse;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

    Item item = item("ENERGIA", TipoItem.RECEITA, "10.00", LocalDate.of(2026, 3, 31), 10, 0);

    when(itemRepository.findAll()).thenReturn(List.of(item));

    RelatorioFinanceiroResponse response = service.gerar(adminAuth(), null);

    assertEquals(1, response.receitas().size());
    assertEquals("ENERGIA", response.receitas().getFirst().descricao());
    assertEquals(BigDecimal.ZERO, response.totalDespesas());
  }

  @Test
  @DisplayName("Deve proibir filtro por role para usuario nao admin")
  void deveProibirFiltroPorRoleParaUsuarioNaoAdmin() {
    RelatorioFinanceiroService service = service();
    UsernamePasswordAuthenticationToken authentication = userAuth("ROLE_MANAGER");

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

    when(roleRepository.findAll()).thenReturn(List.of(financeiro, admin, admin));

    List<String> roles = service.listarRolesDisponiveis(adminAuth());

    assertEquals(List.of("ADMIN", "FINANCEIRO"), roles);
  }

  @Test
  @DisplayName("Deve proibir listagem de roles para usuario nao admin")
  void deveProibirListagemDeRolesParaUsuarioNaoAdmin() {
    RelatorioFinanceiroService service = service();
    UsernamePasswordAuthenticationToken authentication = userAuth("ROLE_USER");

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class, () -> service.listarRolesDisponiveis(authentication));

    assertEquals(403, exception.getStatusCode().value());
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
    Item receita = item("SERVICOS", TipoItem.RECEITA, "50.00", LocalDate.of(2026, 4, 1), 9, 0);

    when(itemRepository.findAllVisiveisPorRoleNomes(Set.of("FINANCEIRO")))
        .thenReturn(List.of(receita));

    RelatorioFinanceiroResponse response = service.gerar(adminAuth(), "financeiro");

    verify(itemRepository).findAllVisiveisPorRoleNomes(Set.of("FINANCEIRO"));
    assertEquals(new BigDecimal("50.00"), response.totalReceitas());
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
            new BigDecimal("300.00"),
            new BigDecimal("700.00"),
            List.of(
                dto("SERVICOS", TipoItem.RECEITA, "1000.00", LocalDate.of(2026, 4, 1), 8, 0)),
            List.of(
                dto("Alimentação", TipoItem.DESPESA, "200.00", LocalDate.of(2026, 4, 2), 9, 0),
                dto("Telefone", TipoItem.DESPESA, "70.00", LocalDate.of(2026, 4, 3), 10, 0),
                dto("Categoria Livre", TipoItem.DESPESA, "30.00", LocalDate.of(2026, 4, 4), 11, 0)));
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
    assertEquals("#ef4444", data.categoriasDespesa().getFirst().cor());
    assertEquals("Telefone", data.categoriasDespesa().get(1).nome());
    assertEquals("#8b5cf6", data.categoriasDespesa().get(1).cor());
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
        new RelatorioFinanceiroResponse(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of());
    byte[] pdf = "pdf".getBytes(StandardCharsets.UTF_8);
    ArgumentCaptor<RelatorioFinanceiroPdfData> captor =
        ArgumentCaptor.forClass(RelatorioFinanceiroPdfData.class);

    when(usuarioRepository.findByEmail("sem-nome@email.com")).thenReturn(Optional.empty());
    when(playwrightPdfService.generateFinancialReportPdf(captor.capture())).thenReturn(pdf);

    service.gerarPdf(authentication("sem-nome@email.com", "ROLE_ADMIN"), relatorio);

    RelatorioFinanceiroPdfData data = captor.getValue();
    assertEquals("sem-nome@email.com", data.responsavel());
    assertEquals("Sem periodo definido", data.periodo());
    assertEquals("O periodo fechou em equilibrio entre entradas e saidas.", data.resultadoDescricao());
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
            new BigDecimal("25.00"),
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

  private UsernamePasswordAuthenticationToken userAuth(String role) {
    return authentication("user@email.com", role);
  }

  private UsernamePasswordAuthenticationToken authentication(String email, String role) {
    return new UsernamePasswordAuthenticationToken(
        email, "senha", List.of(new SimpleGrantedAuthority(role)));
  }

  private Item item(
      String descricao,
      TipoItem tipo,
      String valor,
      LocalDate data,
      Integer hour,
      Integer minute) {
    Item item = new Item();
    item.setId(UUID.randomUUID());
    item.setTipo(tipo);
    item.setValor(new BigDecimal(valor));
    item.setData(data);
    if (hour != null && minute != null && data != null) {
      item.setHorarioCriacao(LocalDateTime.of(data, java.time.LocalTime.of(hour, minute)));
    }
    item.setDescricao(descricao);
    return item;
  }

  private com.sistema_contabilidade.relatorio.dto.RelatorioItemDto dto(
      String descricao,
      TipoItem tipo,
      String valor,
      LocalDate data,
      Integer hour,
      Integer minute) {
    LocalDateTime horario =
        hour == null || minute == null || data == null
            ? null
            : LocalDateTime.of(data, java.time.LocalTime.of(hour, minute));
    return new com.sistema_contabilidade.relatorio.dto.RelatorioItemDto(
        UUID.randomUUID(), tipo, new BigDecimal(valor), data, horario, descricao);
  }
}
