package com.sistema_contabilidade.item.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.item.dto.ItemListPageRequest;
import com.sistema_contabilidade.item.dto.ItemListPageResponse;
import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.security.validation.InputSanitizer;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
  @DisplayName("Deve listar pagina de itens para admin sem restringir role")
  void listarParaAdminSemFiltroDeveRetornarPagina() {
    ItemListService service =
        new ItemListService(
            itemRepository, usuarioRepository, roleRepository, new InputSanitizer());
    Item item = novoItem("ADMIN");
    when(itemRepository.findAll(
            org.mockito.ArgumentMatchers
                .<org.springframework.data.jpa.domain.Specification<Item>>any(),
            any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(item),
                PageRequest.of(
                    0, 10, Sort.by(Sort.Order.desc("horarioCriacao"), Sort.Order.desc("id"))),
                1));

    ItemListPageResponse resultado =
        service.listarItens(autenticacao("admin@email.com", "ADMIN"), new ItemListPageRequest());

    assertEquals(1, resultado.items().size());
    assertEquals(item.getId(), resultado.items().getFirst().id());
    verify(itemRepository)
        .findAll(
            org.mockito.ArgumentMatchers
                .<org.springframework.data.jpa.domain.Specification<Item>>any(),
            argThat(
                (org.springframework.data.domain.Pageable pageable) ->
                    pageable.getPageNumber() == 0 && pageable.getPageSize() == 10));
  }

  @Test
  @DisplayName("Deve listar pagina filtrada pela role selecionada do usuario")
  void listarComRoleSelecionadaDeveFiltrarPorRole() {
    ItemListService service =
        new ItemListService(
            itemRepository, usuarioRepository, roleRepository, new InputSanitizer());
    ItemListPageRequest request = new ItemListPageRequest();
    request.setRole("financeiro");
    when(usuarioRepository.findByEmail("operador@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("operador@email.com", "OPERADOR", "FINANCEIRO")));
    when(itemRepository.findAll(
            org.mockito.ArgumentMatchers
                .<org.springframework.data.jpa.domain.Specification<Item>>any(),
            any(org.springframework.data.domain.Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    service.listarItens(autenticacao("operador@email.com", "OPERADOR"), request);

    verify(itemRepository)
        .findAll(
            org.mockito.ArgumentMatchers
                .<org.springframework.data.jpa.domain.Specification<Item>>any(),
            argThat(
                (org.springframework.data.domain.Pageable pageable) ->
                    pageable.getPageNumber() == 0 && pageable.getPageSize() == 10));
  }

  @Test
  @DisplayName("Deve retornar forbidden quando usuario filtrar por role que nao possui")
  void listarComRoleInvalidaDeveRetornarForbidden() {
    ItemListService service =
        new ItemListService(
            itemRepository, usuarioRepository, roleRepository, new InputSanitizer());
    ItemListPageRequest request = new ItemListPageRequest();
    request.setRole("financeiro");
    UsernamePasswordAuthenticationToken authentication =
        autenticacao("operador@email.com", "OPERADOR");
    when(usuarioRepository.findByEmail("operador@email.com"))
        .thenReturn(Optional.of(usuarioComRoles("operador@email.com", "OPERADOR")));

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> service.listarItens(authentication, request));

    assertEquals(403, ex.getStatusCode().value());
    verify(itemRepository, never())
        .findAll(
            org.mockito.ArgumentMatchers
                .<org.springframework.data.jpa.domain.Specification<Item>>any(),
            any(org.springframework.data.domain.Pageable.class));
  }

  @Test
  @DisplayName("Deve retornar bad request quando intervalo de datas for invalido")
  void listarComIntervaloInvalidoDeveRetornarBadRequest() {
    ItemListService service =
        new ItemListService(
            itemRepository, usuarioRepository, roleRepository, new InputSanitizer());
    ItemListPageRequest request = new ItemListPageRequest();
    request.setDataInicio(LocalDate.of(2026, 5, 10));
    request.setDataFim(LocalDate.of(2026, 5, 1));
    UsernamePasswordAuthenticationToken authentication = autenticacao("admin@email.com", "ADMIN");

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> service.listarItens(authentication, request));

    assertEquals(400, ex.getStatusCode().value());
  }

  @Test
  @DisplayName("Deve listar roles disponiveis para admin usando nomes do banco")
  void listarRolesDisponiveisParaAdminDeveVirDoBanco() {
    ItemListService service =
        new ItemListService(
            itemRepository, usuarioRepository, roleRepository, new InputSanitizer());
    when(roleRepository.findAllRoleNamesOrdered())
        .thenReturn(List.of("ADMIN", "CANDIDATO", "CONTABIL", "FINANCEIRO", "MANAGER", "SUPPORT"));

    List<String> roles = service.listarRolesDisponiveis(autenticacao("admin@email.com", "ADMIN"));

    assertEquals(List.of("FINANCEIRO"), roles);
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

  private Item novoItem(String roleNome) {
    Item item = new Item();
    item.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    item.setValor(new BigDecimal("120.50"));
    item.setData(LocalDate.of(2026, 4, 8));
    item.setHorarioCriacao(LocalDateTime.of(2026, 4, 8, 10, 30));
    item.setCaminhoArquivoPdf("uploads/itens/admin.pdf");
    item.setTipo(TipoItem.DESPESA);
    item.setRoleNome(roleNome);
    item.setDescricao("SERVICOS");
    item.setRazaoSocialNome("EMPRESA TESTE");
    item.setCnpjCpf("123.456.789-00");
    item.setObservacao("Observacao");
    return item;
  }
}
