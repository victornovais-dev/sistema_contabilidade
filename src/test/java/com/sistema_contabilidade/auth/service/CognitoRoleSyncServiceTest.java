package com.sistema_contabilidade.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.auth.config.CognitoProperties;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.usuario.model.Usuario;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CognitoRoleSyncService unit tests")
class CognitoRoleSyncServiceTest {

  @Mock private RoleRepository roleRepository;

  @Test
  @DisplayName("Deve normalizar grupo Cognito com hifen para role local com espacos")
  void deveNormalizarGrupoCognitoComHifenParaRoleLocalComEspacos() {
    CognitoProperties cognitoProperties = new CognitoProperties();
    CognitoRoleSyncService service = new CognitoRoleSyncService(roleRepository, cognitoProperties);

    String resultado = service.normalizeGroup("ANDRE-DO-PRADO");

    assertEquals("ANDRE DO PRADO", resultado);
  }

  @Test
  @DisplayName("Deve reutilizar role local existente ao sincronizar grupo Cognito")
  void deveReutilizarRoleLocalExistenteAoSincronizarGrupoCognito() {
    CognitoProperties cognitoProperties = new CognitoProperties();
    CognitoRoleSyncService service = new CognitoRoleSyncService(roleRepository, cognitoProperties);
    Usuario usuario = new Usuario();
    Role role = new Role();
    role.setNome("PAULO FREIRE");
    when(roleRepository.findByNomeIgnoreCase("PAULO FREIRE")).thenReturn(Optional.of(role));

    CognitoRoleSyncResult result = service.syncMemberships(usuario, Set.of("PAULO_FREIRE"));

    assertEquals(Set.of("PAULO FREIRE"), result.normalizedGroups());
    assertTrue(
        usuario.getRoles().stream().anyMatch(entry -> "PAULO FREIRE".equals(entry.getNome())));
    verify(roleRepository, never()).save(any(Role.class));
  }

  @Test
  @DisplayName("Deve criar role local ausente ao sincronizar grupo Cognito")
  void deveCriarRoleLocalAusenteAoSincronizarGrupoCognito() {
    CognitoProperties cognitoProperties = new CognitoProperties();
    CognitoRoleSyncService service = new CognitoRoleSyncService(roleRepository, cognitoProperties);
    Usuario usuario = new Usuario();
    Role roleCriada = new Role();
    roleCriada.setNome("PAULO FREIRE");
    when(roleRepository.findByNomeIgnoreCase("PAULO FREIRE")).thenReturn(Optional.empty());
    when(roleRepository.save(any(Role.class)))
        .thenAnswer(
            invocation -> {
              Role role = invocation.getArgument(0);
              role.setNome("PAULO FREIRE");
              return role;
            });

    CognitoRoleSyncResult result = service.syncMemberships(usuario, Set.of("PAULO_FREIRE"));

    assertEquals(Set.of("PAULO FREIRE"), result.normalizedGroups());
    assertTrue(
        usuario.getRoles().stream().anyMatch(entry -> "PAULO FREIRE".equals(entry.getNome())));
    verify(roleRepository).save(any(Role.class));
  }
}
