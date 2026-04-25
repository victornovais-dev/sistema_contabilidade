package com.sistema_contabilidade.notificacao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.notificacao.dto.NotificacaoListResponse;
import com.sistema_contabilidade.notificacao.model.Notificacao;
import com.sistema_contabilidade.notificacao.repository.NotificacaoRepository;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificacaoService unit tests")
class NotificacaoServiceTest {

  @Mock private ItemRepository itemRepository;
  @Mock private NotificacaoRepository notificacaoRepository;
  @Mock private UsuarioRepository usuarioRepository;
  @Mock private RoleRepository roleRepository;

  @Test
  @DisplayName("Deve salvar notificacao quando item criado for receita")
  void registrarReceitaLancadaDeveSalvarQuandoItemForReceita() {
    NotificacaoService service =
        new NotificacaoService(
            itemRepository, notificacaoRepository, usuarioRepository, roleRepository);
    Item item = new Item();
    item.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    item.setTipo(TipoItem.RECEITA);
    item.setRoleNome("manager");
    item.setDescricao("CONTA DC");
    item.setRazaoSocialNome("GOV SP");
    item.setValor(new BigDecimal("100000.00"));
    item.setHorarioCriacao(LocalDateTime.of(2026, 4, 14, 10, 15));
    when(notificacaoRepository.findFirstByItemId(item.getId())).thenReturn(Optional.empty());

    service.registrarReceitaLancada(item);

    ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
    verify(notificacaoRepository).save(captor.capture());
    assertEquals(item.getId(), captor.getValue().getItemId());
    assertEquals("MANAGER", captor.getValue().getRoleNome());
    assertEquals("CONTA DC", captor.getValue().getDescricao());
    assertEquals(new BigDecimal("100000.00"), captor.getValue().getValor());
  }

  @Test
  @DisplayName("Nao deve salvar notificacao quando item criado for despesa")
  void registrarReceitaLancadaNaoDeveSalvarQuandoItemNaoForReceita() {
    NotificacaoService service =
        new NotificacaoService(
            itemRepository, notificacaoRepository, usuarioRepository, roleRepository);
    Item item = new Item();
    item.setId(UUID.fromString("99999999-1111-1111-1111-111111111111"));
    item.setTipo(TipoItem.DESPESA);

    service.registrarReceitaLancada(item);

    verify(notificacaoRepository, never()).save(any());
    verify(notificacaoRepository).deleteByItemId(item.getId());
  }

  @Test
  @DisplayName("Deve atualizar notificacao existente quando item de receita mudar")
  void sincronizarComItemDeveAtualizarNotificacaoExistente() {
    NotificacaoService service =
        new NotificacaoService(
            itemRepository, notificacaoRepository, usuarioRepository, roleRepository);
    UUID itemId = UUID.fromString("12121212-3434-5656-7878-909090909090");
    Item item = new Item();
    item.setId(itemId);
    item.setTipo(TipoItem.RECEITA);
    item.setRoleNome("admin");
    item.setDescricao("CONTA FEFC");
    item.setRazaoSocialNome("GOV BR");
    item.setValor(new BigDecimal("4200.50"));
    item.setHorarioCriacao(LocalDateTime.of(2026, 4, 15, 9, 45));

    Notificacao existente = new Notificacao();
    existente.setId(UUID.fromString("aaaaaaaa-1111-2222-3333-cccccccccccc"));
    existente.setItemId(itemId);
    existente.setLimpa(true);

    when(notificacaoRepository.findFirstByItemId(itemId)).thenReturn(Optional.of(existente));

    service.sincronizarComItem(item);

    ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
    verify(notificacaoRepository).save(captor.capture());
    assertEquals(existente.getId(), captor.getValue().getId());
    assertEquals(itemId, captor.getValue().getItemId());
    assertEquals("ADMIN", captor.getValue().getRoleNome());
    assertEquals("CONTA FEFC", captor.getValue().getDescricao());
    assertTrue(captor.getValue().isLimpa());
  }

  @Test
  @DisplayName("Deve remover notificacao pelo item")
  void removerPorItemIdDeveDelegarAoRepositorio() {
    NotificacaoService service =
        new NotificacaoService(
            itemRepository, notificacaoRepository, usuarioRepository, roleRepository);
    UUID itemId = UUID.fromString("45454545-6666-7777-8888-999999999999");

    service.removerPorItemId(itemId);

    verify(notificacaoRepository, times(1)).deleteByItemId(itemId);
  }

  @Test
  @DisplayName("Deve listar notificacoes para admin sem filtro")
  void listarDeveRetornarTodasNotificacoesParaAdmin() {
    NotificacaoService service =
        new NotificacaoService(
            itemRepository, notificacaoRepository, usuarioRepository, roleRepository);
    when(itemRepository.findReceitasOrderByHorarioCriacaoDesc()).thenReturn(List.of());
    when(notificacaoRepository.findAllResumoOrderByCriadoEmDesc())
        .thenReturn(
            List.of(
                new NotificacaoListResponse(
                    UUID.fromString("aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb"),
                    UUID.fromString("cccccccc-1111-2222-3333-dddddddddddd"),
                    "MANAGER",
                    "CONTA FP",
                    "Fornecedor",
                    new BigDecimal("5000.00"),
                    LocalDateTime.of(2026, 4, 14, 12, 0),
                    false)));

    List<NotificacaoListResponse> response =
        service.listar(autenticacao("admin@email.com", "ADMIN"), null);

    assertEquals(1, response.size());
    verify(notificacaoRepository).deleteOrfasOuInvalidas();
    verify(notificacaoRepository).findAllResumoOrderByCriadoEmDesc();
  }

  @Test
  @DisplayName("Deve validar receitas sem notificacao antes de listar")
  void listarDeveSincronizarReceitasSemNotificacao() {
    NotificacaoService service =
        new NotificacaoService(
            itemRepository, notificacaoRepository, usuarioRepository, roleRepository);
    Item item = new Item();
    item.setId(UUID.fromString("67676767-1111-2222-3333-444444444444"));
    item.setTipo(TipoItem.RECEITA);
    item.setRoleNome("ADMIN");
    item.setDescricao("CONTA FP");
    item.setRazaoSocialNome("Fornecedor Teste");
    item.setValor(new BigDecimal("3500.00"));
    item.setHorarioCriacao(LocalDateTime.of(2026, 4, 15, 13, 0));

    when(itemRepository.findReceitasOrderByHorarioCriacaoDesc()).thenReturn(List.of(item));
    when(notificacaoRepository.findFirstByItemId(item.getId())).thenReturn(Optional.empty());
    when(notificacaoRepository.findAllResumoOrderByCriadoEmDesc()).thenReturn(List.of());

    service.listar(autenticacao("admin@email.com", "ADMIN"), null);

    verify(notificacaoRepository).deleteOrfasOuInvalidas();
    verify(notificacaoRepository).save(any(Notificacao.class));
    verify(notificacaoRepository).findAllResumoOrderByCriadoEmDesc();
  }

  @Test
  @DisplayName("Deve filtrar notificacoes pela role selecionada do usuario")
  void listarDeveFiltrarNotificacoesPelaRoleDoUsuario() {
    NotificacaoService service =
        new NotificacaoService(
            itemRepository, notificacaoRepository, usuarioRepository, roleRepository);
    when(usuarioRepository.findByEmail("operador@email.com"))
        .thenReturn(Optional.of(usuarioComRole("operador@email.com", "OPERADOR")));
    when(itemRepository.findReceitasPorRoleNomeOrderByHorarioCriacaoDesc("OPERADOR"))
        .thenReturn(List.of());
    when(notificacaoRepository.findResumoByRoleNomeOrderByCriadoEmDesc("OPERADOR"))
        .thenReturn(List.of());

    service.listar(autenticacao("operador@email.com", "OPERADOR"), "operador");

    verify(notificacaoRepository).deleteOrfasOuInvalidas();
    verify(notificacaoRepository).findResumoByRoleNomeOrderByCriadoEmDesc("OPERADOR");
  }

  @Test
  @DisplayName("Deve retornar roles disponiveis para admin")
  void listarRolesDisponiveisDeveRetornarTodasParaAdmin() {
    NotificacaoService service =
        new NotificacaoService(
            itemRepository, notificacaoRepository, usuarioRepository, roleRepository);
    when(roleRepository.findAllRoleNamesOrdered())
        .thenReturn(List.of("ADMIN", "CANDIDATO", "CONTABIL", "FINANCEIRO", "MANAGER", "SUPPORT"));

    List<String> roles = service.listarRolesDisponiveis(autenticacao("admin@email.com", "ADMIN"));

    assertEquals(List.of("FINANCEIRO"), roles);
  }

  @Test
  @DisplayName("Deve retornar roles do proprio usuario quando nao for admin")
  void listarRolesDisponiveisDeveRetornarRolesDoUsuario() {
    NotificacaoService service =
        new NotificacaoService(
            itemRepository, notificacaoRepository, usuarioRepository, roleRepository);
    when(usuarioRepository.findByEmail("manager@email.com"))
        .thenReturn(Optional.of(usuarioComRole("manager@email.com", "MANAGER", "FINANCEIRO")));

    List<String> roles =
        service.listarRolesDisponiveis(autenticacao("manager@email.com", "MANAGER"));

    assertEquals(1, roles.size());
    assertTrue(roles.contains("FINANCEIRO"));
  }

  @Test
  @DisplayName("Deve atualizar limpeza da notificacao")
  void atualizarLimpezaDevePersistirNovoEstado() {
    NotificacaoService service =
        new NotificacaoService(
            itemRepository, notificacaoRepository, usuarioRepository, roleRepository);
    Notificacao notificacao = new Notificacao();
    UUID id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    notificacao.setId(id);
    notificacao.setItemId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
    notificacao.setRoleNome("MANAGER");
    notificacao.setDescricao("CONTA DC");
    notificacao.setRazaoSocialNome("GOV SP");
    notificacao.setValor(new BigDecimal("1000.00"));
    notificacao.setCriadoEm(LocalDateTime.of(2026, 4, 14, 10, 15));
    notificacao.setLimpa(false);

    when(notificacaoRepository.findById(id)).thenReturn(Optional.of(notificacao));
    doAnswer(invocation -> invocation.getArgument(0))
        .when(notificacaoRepository)
        .save(any(Notificacao.class));
    when(usuarioRepository.findByEmail("manager@email.com"))
        .thenReturn(Optional.of(usuarioComRole("manager@email.com", "MANAGER")));

    NotificacaoListResponse response =
        service.atualizarLimpeza(autenticacao("manager@email.com", "MANAGER"), id, true);

    assertTrue(response.limpa());
    assertTrue(notificacao.isLimpa());
  }

  private UsernamePasswordAuthenticationToken autenticacao(String email, String... roles) {
    return new UsernamePasswordAuthenticationToken(
        email,
        "n/a",
        java.util.Arrays.stream(roles)
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList());
  }

  private Usuario usuarioComRole(String email, String... roleNomes) {
    Usuario usuario = new Usuario();
    usuario.setEmail(email);
    usuario.setNome(email);
    usuario.setSenha("123456");
    for (String roleNome : roleNomes) {
      Role role = new Role();
      role.setNome(roleNome);
      usuario.getRoles().add(role);
    }
    return usuario;
  }
}
