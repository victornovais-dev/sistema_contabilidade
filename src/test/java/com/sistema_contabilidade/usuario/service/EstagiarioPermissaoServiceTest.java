package com.sistema_contabilidade.usuario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.rbac.dto.RoleResumoDto;
import com.sistema_contabilidade.rbac.dto.UsuarioComRolesDto;
import com.sistema_contabilidade.rbac.service.CandidateRoleCatalogService;
import com.sistema_contabilidade.usuario.dto.EstagiarioPermissoesUpdateRequest;
import com.sistema_contabilidade.usuario.dto.UsuarioUpdateByEmailRequest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("EstagiarioPermissaoService tests")
class EstagiarioPermissaoServiceTest {

  @Mock private UsuarioService usuarioService;
  @Mock private CandidateRoleCatalogService candidateRoleCatalogService;
  @InjectMocks private EstagiarioPermissaoService estagiarioPermissaoService;

  @Test
  @DisplayName("Deve preservar ESTAGIARIO ao salvar as permissoes de campanha")
  void devePreservarEstagiarioAoSalvarPermissoesDeCampanha() {
    UsuarioComRolesDto estagiario = usuario("estagiario@email.com", "ESTAGIARIO", "CAMPANHA A");
    when(usuarioService.findComRolesByEmail("estagiario@email.com")).thenReturn(estagiario);
    when(candidateRoleCatalogService.listAvailableRolesForAdmin())
        .thenReturn(List.of("CAMPANHA A", "CAMPANHA B"));
    when(usuarioService.updateByEmail(any(UsuarioUpdateByEmailRequest.class)))
        .thenReturn(estagiario);

    estagiarioPermissaoService.atualizarPermissoes(
        new EstagiarioPermissoesUpdateRequest("estagiario@email.com", Set.of("campanha b")));

    ArgumentCaptor<UsuarioUpdateByEmailRequest> requestCaptor =
        ArgumentCaptor.forClass(UsuarioUpdateByEmailRequest.class);
    verify(usuarioService).updateByEmail(requestCaptor.capture());
    assertEquals("estagiario@email.com", requestCaptor.getValue().email());
    assertEquals(null, requestCaptor.getValue().senha());
    assertEquals(Set.of("ESTAGIARIO", "CAMPANHA B"), requestCaptor.getValue().roles());
  }

  @Test
  @DisplayName("Deve bloquear atribuicao de CANDIDATO, CONTABIL e ADMIN ao estagiario")
  void deveBloquearAtribuicaoDeRolesTecnicasAoEstagiario() {
    when(usuarioService.findComRolesByEmail("estagiario@email.com"))
        .thenReturn(usuario("estagiario@email.com", "ESTAGIARIO"));
    when(candidateRoleCatalogService.listAvailableRolesForAdmin())
        .thenReturn(List.of("CAMPANHA A"));

    for (String roleTecnica : List.of("CANDIDATO", "CONTABIL", "ADMIN")) {
      EstagiarioPermissoesUpdateRequest request =
          new EstagiarioPermissoesUpdateRequest("estagiario@email.com", Set.of(roleTecnica));
      ResponseStatusException exception =
          assertThrows(
              ResponseStatusException.class,
              () -> estagiarioPermissaoService.atualizarPermissoes(request));

      assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
    verify(usuarioService, never()).updateByEmail(any());
  }

  @Test
  @DisplayName("Deve bloquear alteracao de usuario que nao seja somente ESTAGIARIO")
  void deveBloquearAlteracaoDeUsuarioQueNaoSejaSomenteEstagiario() {
    when(usuarioService.findComRolesByEmail("admin@email.com"))
        .thenReturn(usuario("admin@email.com", "ESTAGIARIO", "ADMIN"));

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> estagiarioPermissaoService.buscarPorEmail("admin@email.com"));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
  }

  private UsuarioComRolesDto usuario(String email, String... roles) {
    Set<RoleResumoDto> resumoRoles =
        java.util.Arrays.stream(roles)
            .map(role -> new RoleResumoDto(UUID.randomUUID(), role))
            .collect(java.util.stream.Collectors.toSet());
    return new UsuarioComRolesDto(UUID.randomUUID(), "Estagiario", email, resumoRoles);
  }
}
