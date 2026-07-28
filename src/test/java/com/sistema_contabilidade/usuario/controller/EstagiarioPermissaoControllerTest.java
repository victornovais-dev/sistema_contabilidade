package com.sistema_contabilidade.usuario.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.rbac.dto.RoleResumoDto;
import com.sistema_contabilidade.rbac.dto.UsuarioComRolesDto;
import com.sistema_contabilidade.usuario.dto.EstagiarioPermissoesUpdateRequest;
import com.sistema_contabilidade.usuario.service.EstagiarioPermissaoService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
@DisplayName("EstagiarioPermissaoController tests")
class EstagiarioPermissaoControllerTest {

  @Mock private EstagiarioPermissaoService estagiarioPermissaoService;
  @InjectMocks private EstagiarioPermissaoController estagiarioPermissaoController;

  @Test
  @DisplayName("Deve exigir role CONTABIL em todos os endpoints")
  void deveExigirRoleContabilEmTodosOsEndpoints() {
    PreAuthorize authorization =
        EstagiarioPermissaoController.class.getAnnotation(PreAuthorize.class);

    assertEquals("hasRole('CONTABIL')", authorization.value());
  }

  @Test
  @DisplayName("Deve atualizar permissoes delegando para o service")
  void deveAtualizarPermissoesDelegandoParaService() {
    EstagiarioPermissoesUpdateRequest request =
        new EstagiarioPermissoesUpdateRequest("estagiario@email.com", Set.of("CAMPANHA A"));
    UsuarioComRolesDto usuario =
        new UsuarioComRolesDto(
            UUID.randomUUID(),
            "Estagiario",
            "estagiario@email.com",
            Set.of(new RoleResumoDto(UUID.randomUUID(), "ESTAGIARIO")));
    when(estagiarioPermissaoService.atualizarPermissoes(request)).thenReturn(usuario);

    var response = estagiarioPermissaoController.atualizarPermissoes(request);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("estagiario@email.com", response.getBody().getEmail());
    verify(estagiarioPermissaoService).atualizarPermissoes(request);
  }
}
