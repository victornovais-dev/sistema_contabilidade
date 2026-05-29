package com.sistema_contabilidade.item.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.item.dto.ItemListPageRequest;
import com.sistema_contabilidade.item.dto.ItemListPageResponse;
import com.sistema_contabilidade.item.dto.ItemListResponse;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemListPageQuery;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.security.validation.InputSanitizer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
  @Mock private RoleRepository roleRepository;

  @Test
  @DisplayName("Deve listar pagina de itens para admin sem restringir role")
  void listarParaAdminSemFiltroDeveRetornarPagina() {
    ItemListService service =
        new ItemListService(itemRepository, roleRepository, new InputSanitizer());
    ItemListResponse item = novoResumo("ADMIN");
    when(itemRepository.findPageForList(any(ItemListPageQuery.class), any()))
        .thenReturn(
            new PageImpl<>(
                List.of(item),
                PageRequest.of(
                    0, 10, Sort.by(Sort.Order.desc("horarioCriacao"), Sort.Order.desc("id"))),
                1));

    ItemListPageResponse resultado =
        service.listarItens(autenticacao("admin@email.com", "ADMIN"), new ItemListPageRequest());

    assertEquals(1, resultado.items().size());
    assertEquals(item.id(), resultado.items().getFirst().id());
    verify(itemRepository)
        .findPageForList(
            argThat(
                (ItemListPageQuery query) ->
                    query.roleNomes().isEmpty()
                        && query.tipo() == null
                        && query.dataInicio() == null
                        && query.dataFim() == null
                        && query.descricao() == null
                        && query.razao() == null),
            argThat(
                (org.springframework.data.domain.Pageable pageable) ->
                    pageable.getPageNumber() == 0 && pageable.getPageSize() == 10));
  }

  @Test
  @DisplayName("Deve listar pagina filtrada pela role selecionada do usuario")
  void listarComRoleSelecionadaDeveFiltrarPorRole() {
    ItemListService service =
        new ItemListService(itemRepository, roleRepository, new InputSanitizer());
    ItemListPageRequest request = new ItemListPageRequest();
    request.setRole("financeiro");
    when(itemRepository.findPageForList(any(ItemListPageQuery.class), any()))
        .thenReturn(new PageImpl<>(List.of()));

    service.listarItens(autenticacao("operador@email.com", "OPERADOR", "FINANCEIRO"), request);

    verify(itemRepository)
        .findPageForList(
            argThat(
                (ItemListPageQuery query) ->
                    query.roleNomes() != null
                        && query.roleNomes().equals(java.util.Set.of("FINANCEIRO"))
                        && query.tipo() == null
                        && query.descricao() == null
                        && query.razao() == null),
            argThat(
                (org.springframework.data.domain.Pageable pageable) ->
                    pageable.getPageNumber() == 0 && pageable.getPageSize() == 10));
  }

  @Test
  @DisplayName("Deve retornar forbidden quando usuario filtrar por role que nao possui")
  void listarComRoleInvalidaDeveRetornarForbidden() {
    ItemListService service =
        new ItemListService(itemRepository, roleRepository, new InputSanitizer());
    ItemListPageRequest request = new ItemListPageRequest();
    request.setRole("financeiro");
    UsernamePasswordAuthenticationToken authentication =
        autenticacao("operador@email.com", "OPERADOR");

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> service.listarItens(authentication, request));

    assertEquals(403, ex.getStatusCode().value());
    verify(itemRepository, never()).findPageForList(any(ItemListPageQuery.class), any());
  }

  @Test
  @DisplayName("Deve retornar bad request quando intervalo de datas for invalido")
  void listarComIntervaloInvalidoDeveRetornarBadRequest() {
    ItemListService service =
        new ItemListService(itemRepository, roleRepository, new InputSanitizer());
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
        new ItemListService(itemRepository, roleRepository, new InputSanitizer());
    when(roleRepository.findAllRoleNamesOrdered())
        .thenReturn(List.of("ADMIN", "CANDIDATO", "CONTABIL", "FINANCEIRO", "MANAGER", "SUPPORT"));

    List<String> roles = service.listarRolesDisponiveis(autenticacao("admin@email.com", "ADMIN"));

    assertEquals(List.of("FINANCEIRO"), roles);
    verify(roleRepository).findAllRoleNamesOrdered();
  }

  @Test
  @DisplayName("Deve retornar pagina vazia quando usuario nao possuir roles visiveis")
  void deveRetornarPaginaVaziaQuandoUsuarioNaoPossuirRolesVisiveis() {
    ItemListService service =
        new ItemListService(itemRepository, roleRepository, new InputSanitizer());

    ItemListPageResponse resultado =
        service.listarItens(
            new UsernamePasswordAuthenticationToken("sem-role@email.com", "n/a", List.of()),
            new ItemListPageRequest());

    assertTrue(resultado.items().isEmpty());
    verify(itemRepository, never()).findPageForList(any(ItemListPageQuery.class), any());
  }

  @Test
  @DisplayName(
      "Deve reajustar pagina para ultima pagina valida quando pagina solicitada estiver vazia")
  void deveReajustarPaginaParaUltimaPaginaValidaQuandoPaginaSolicitadaEstiverVazia() {
    ItemListService service =
        new ItemListService(itemRepository, roleRepository, new InputSanitizer());
    ItemListPageRequest request = new ItemListPageRequest();
    request.setPage(5);
    request.setPageSize(10);
    ItemListResponse item = novoResumo("ADMIN");
    Sort sort = Sort.by(Sort.Order.desc("horarioCriacao"), Sort.Order.desc("id"));

    when(itemRepository.findPageForList(any(ItemListPageQuery.class), any()))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(4, 10, sort), 11))
        .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(1, 10, sort), 11));

    ItemListPageResponse resultado =
        service.listarItens(autenticacao("admin@email.com", "ADMIN"), request);

    assertEquals(1, resultado.items().size());
    verify(itemRepository, times(2)).findPageForList(any(ItemListPageQuery.class), any());
  }

  @Test
  @DisplayName("Deve listar roles do proprio usuario quando nao for admin")
  void listarRolesDisponiveisParaUsuarioComumDeveVirDaAutenticacao() {
    ItemListService service =
        new ItemListService(itemRepository, roleRepository, new InputSanitizer());

    List<String> roles =
        service.listarRolesDisponiveis(autenticacao("operador@email.com", "OPERADOR", "SUPPORT"));

    assertEquals(List.of("OPERADOR"), roles);
    verify(roleRepository, never()).findAllRoleNamesOrdered();
  }

  private UsernamePasswordAuthenticationToken autenticacao(String email, String... roles) {
    return new UsernamePasswordAuthenticationToken(
        email,
        "n/a",
        java.util.Arrays.stream(roles)
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList());
  }

  private ItemListResponse novoResumo(String roleNome) {
    return new ItemListResponse(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        new BigDecimal("120.50"),
        LocalDate.of(2026, 4, 8),
        LocalDateTime.of(2026, 4, 8, 10, 30),
        "uploads/itens/admin.pdf",
        TipoItem.DESPESA,
        roleNome,
        "SERVICOS",
        "EMPRESA TESTE",
        "123.456.789-00",
        "Observacao",
        false,
        true);
  }
}
