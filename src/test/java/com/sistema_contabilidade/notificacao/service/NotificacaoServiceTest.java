package com.sistema_contabilidade.notificacao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.notificacao.dto.NotificacaoListResponse;
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

  @Mock private NotificacaoRepository notificacaoRepository;
  @Mock private UsuarioRepository usuarioRepository;
  @Mock private RoleRepository roleRepository;

  @Test
  @DisplayName("Deve salvar notificacao quando item criado for receita")
  void registrarReceitaLancadaDeveSalvarQuandoItemForReceita() {
    NotificacaoService service =
        new NotificacaoService(notificacaoRepository, usuarioRepository, roleRepository);
    Item item = new Item();
    item.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    item.setTipo(TipoItem.RECEITA);
    item.setRoleNome("manager");
    item.setDescricao("CONTA DC");
    item.setRazaoSocialNome("GOV SP");
    item.setValor(new BigDecimal("100000.00"));
    item.setHorarioCriacao(LocalDateTime.of(2026, 4, 14, 10, 15));

    service.registrarReceitaLancada(item);

    ArgumentCaptor<com.sistema_contabilidade.notificacao.model.Notificacao> captor =
        ArgumentCaptor.forClass(com.sistema_contabilidade.notificacao.model.Notificacao.class);
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
        new NotificacaoService(notificacaoRepository, usuarioRepository, roleRepository);
    Item item = new Item();
    item.setTipo(TipoItem.DESPESA);

    service.registrarReceitaLancada(item);

    verify(notificacaoRepository, never()).save(any());
  }

  @Test
  @DisplayName("Deve listar notificacoes para admin sem filtro")
  void listarDeveRetornarTodasNotificacoesParaAdmin() {
    NotificacaoService service =
        new NotificacaoService(notificacaoRepository, usuarioRepository, roleRepository);
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
                    LocalDateTime.of(2026, 4, 14, 12, 0))));

    List<NotificacaoListResponse> response =
        service.listar(autenticacao("admin@email.com", "ADMIN"), null);

    assertEquals(1, response.size());
    verify(notificacaoRepository).findAllResumoOrderByCriadoEmDesc();
  }

  @Test
  @DisplayName("Deve filtrar notificacoes pela role selecionada do usuario")
  void listarDeveFiltrarNotificacoesPelaRoleDoUsuario() {
    NotificacaoService service =
        new NotificacaoService(notificacaoRepository, usuarioRepository, roleRepository);
    when(usuarioRepository.findByEmail("operador@email.com"))
        .thenReturn(Optional.of(usuarioComRole("operador@email.com", "OPERADOR")));
    when(notificacaoRepository.findResumoByRoleNomeOrderByCriadoEmDesc(eq("OPERADOR")))
        .thenReturn(List.of());

    service.listar(autenticacao("operador@email.com", "OPERADOR"), "operador");

    verify(notificacaoRepository).findResumoByRoleNomeOrderByCriadoEmDesc("OPERADOR");
  }

  @Test
  @DisplayName("Deve retornar roles disponiveis para admin")
  void listarRolesDisponiveisDeveRetornarTodasParaAdmin() {
    NotificacaoService service =
        new NotificacaoService(notificacaoRepository, usuarioRepository, roleRepository);
    when(roleRepository.findAllRoleNamesOrdered()).thenReturn(List.of("ADMIN", "MANAGER"));

    List<String> roles = service.listarRolesDisponiveis(autenticacao("admin@email.com", "ADMIN"));

    assertEquals(List.of("ADMIN", "MANAGER"), roles);
  }

  @Test
  @DisplayName("Deve retornar roles do proprio usuario quando nao for admin")
  void listarRolesDisponiveisDeveRetornarRolesDoUsuario() {
    NotificacaoService service =
        new NotificacaoService(notificacaoRepository, usuarioRepository, roleRepository);
    when(usuarioRepository.findByEmail("manager@email.com"))
        .thenReturn(Optional.of(usuarioComRole("manager@email.com", "MANAGER")));

    List<String> roles =
        service.listarRolesDisponiveis(autenticacao("manager@email.com", "MANAGER"));

    assertEquals(1, roles.size());
    assertTrue(roles.contains("MANAGER"));
  }

  private UsernamePasswordAuthenticationToken autenticacao(String email, String... roles) {
    return new UsernamePasswordAuthenticationToken(
        email,
        "n/a",
        java.util.Arrays.stream(roles)
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList());
  }

  private Usuario usuarioComRole(String email, String roleNome) {
    Usuario usuario = new Usuario();
    usuario.setEmail(email);
    usuario.setNome(email);
    usuario.setSenha("123456");
    Role role = new Role();
    role.setNome(roleNome);
    usuario.getRoles().add(role);
    return usuario;
  }
}
