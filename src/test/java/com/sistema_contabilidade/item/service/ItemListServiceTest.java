package com.sistema_contabilidade.item.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.item.dto.ItemListResponse;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItemListService unit tests")
class ItemListServiceTest {

  @Mock private ItemRepository itemRepository;
  @Mock private UsuarioRepository usuarioRepository;
  @Mock private RoleRepository roleRepository;

  @Test
  @DisplayName("Deve listar resumos ordenados para admin sem carregar caminho de fetch da lista")
  void listarParaAdminSemFiltroDeveUsarConsultaResumoOtimizada() {
    ItemListService service =
        new ItemListService(itemRepository, usuarioRepository, roleRepository);
    ItemListResponse response =
        new ItemListResponse(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            new BigDecimal("120.50"),
            LocalDate.of(2026, 4, 8),
            LocalDateTime.of(2026, 4, 8, 10, 30),
            "uploads/itens/admin.pdf",
            TipoItem.DESPESA,
            "ADMIN",
            "SERVICOS",
            "EMPRESA TESTE",
            "123.456.789-00",
            "Observacao",
            true);
    when(itemRepository.findAllResumoOrderByHorarioCriacaoDesc()).thenReturn(List.of(response));

    List<ItemListResponse> resultado =
        service.listarItens(autenticacao("admin@email.com", "ADMIN"), null);

    assertEquals(1, resultado.size());
    verify(itemRepository).findAllResumoOrderByHorarioCriacaoDesc();
  }

  @Test
  @DisplayName("Deve listar resumos filtrados pela role selecionada do usuario")
  void listarComRoleSelecionadaDeveUsarConsultaResumoFiltrada() {
    ItemListService service =
        new ItemListService(itemRepository, usuarioRepository, roleRepository);
    when(usuarioRepository.findByEmail("operador@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("operador@email.com", "OPERADOR", "FINANCEIRO")));
    when(itemRepository.findResumoVisiveisPorRoleNomeOrderByHorarioCriacaoDesc("FINANCEIRO"))
        .thenReturn(List.of());

    service.listarItens(autenticacao("operador@email.com", "OPERADOR"), "financeiro");

    verify(itemRepository).findResumoVisiveisPorRoleNomeOrderByHorarioCriacaoDesc("FINANCEIRO");
  }

  @Test
  @DisplayName("Deve retornar forbidden quando usuario filtrar por role que nao possui")
  void listarComRoleInvalidaDeveRetornarForbidden() {
    ItemListService service =
        new ItemListService(itemRepository, usuarioRepository, roleRepository);
    UsernamePasswordAuthenticationToken authentication =
        autenticacao("operador@email.com", "OPERADOR");
    when(usuarioRepository.findByEmail("operador@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("operador@email.com", "OPERADOR")));

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> service.listarItens(authentication, "financeiro"));

    assertEquals(403, ex.getStatusCode().value());
  }

  @Test
  @DisplayName("Deve listar roles disponiveis para admin usando nomes do banco")
  void listarRolesDisponiveisParaAdminDeveVirDoBanco() {
    ItemListService service =
        new ItemListService(itemRepository, usuarioRepository, roleRepository);
    when(roleRepository.findAllRoleNamesOrdered()).thenReturn(List.of("ADMIN", "FINANCEIRO"));

    List<String> roles = service.listarRolesDisponiveis(autenticacao("admin@email.com", "ADMIN"));

    assertEquals(List.of("ADMIN", "FINANCEIRO"), roles);
    verify(roleRepository).findAllRoleNamesOrdered();
  }

  private UsernamePasswordAuthenticationToken autenticacao(String email, String... roles) {
    return new UsernamePasswordAuthenticationToken(
        email,
        "n/a",
        java.util.Arrays.stream(roles)
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList());
  }

  private Usuario usuarioComRoles(String email, String... roleNomes) {
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
