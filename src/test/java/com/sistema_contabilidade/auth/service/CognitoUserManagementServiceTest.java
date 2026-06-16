package com.sistema_contabilidade.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.auth.config.CognitoProperties;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRemoveUserFromGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUpdateUserAttributesRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListGroupsRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListGroupsResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

@ExtendWith(MockitoExtension.class)
@DisplayName("CognitoUserManagementService unit tests")
class CognitoUserManagementServiceTest {

  @Mock private CognitoIdentityProviderClient cognitoIdentityProviderClient;
  @Mock private CognitoAuthProviderStrategy cognitoAuthProviderStrategy;
  @Mock private CognitoRoleSyncService cognitoRoleSyncService;

  private CognitoProperties cognitoProperties;
  private CognitoUserManagementService cognitoUserManagementService;

  @BeforeEach
  void setUp() {
    cognitoProperties = new CognitoProperties();
    cognitoProperties.setUserPoolId("pool-123");
    cognitoUserManagementService =
        new CognitoUserManagementService(
            cognitoIdentityProviderClient,
            cognitoAuthProviderStrategy,
            cognitoProperties,
            cognitoRoleSyncService);
  }

  @Test
  @DisplayName("Deve atualizar grupos Cognito usando nome real do grupo para role com espacos")
  void deveAtualizarGruposCognitoUsandoNomeRealDoGrupoParaRoleComEspacos() {
    CognitoUserProfile currentProfile =
        new CognitoUserProfile(
            "rafa@email.com", "rafa@email.com", "Rafa", "sub-123", Set.of("CONTABIL"));
    CognitoUserProfile updatedProfile =
        new CognitoUserProfile(
            "rafa@email.com", "rafa@email.com", "Rafa", "sub-123", Set.of("ANDRE_DO_PRADO"));

    when(cognitoRoleSyncService.normalizeGroup("ANDRE DO PRADO")).thenReturn("ANDRE DO PRADO");
    when(cognitoRoleSyncService.normalizeGroup("ANDRE_DO_PRADO")).thenReturn("ANDRE DO PRADO");
    when(cognitoIdentityProviderClient.listGroups(any(ListGroupsRequest.class)))
        .thenReturn(
            ListGroupsResponse.builder()
                .groups(GroupType.builder().groupName("ANDRE_DO_PRADO").build())
                .build());
    when(cognitoAuthProviderStrategy.loadProfile("rafa@email.com"))
        .thenReturn(currentProfile)
        .thenReturn(updatedProfile);

    CognitoUserProfile resultado =
        cognitoUserManagementService.updateUser(
            "rafa@email.com", "Rafa", "rafa@email.com", null, Set.of("ANDRE DO PRADO"));

    assertEquals(updatedProfile, resultado);
    verify(cognitoIdentityProviderClient)
        .adminRemoveUserFromGroup(any(AdminRemoveUserFromGroupRequest.class));
    verify(cognitoIdentityProviderClient)
        .adminAddUserToGroup(any(AdminAddUserToGroupRequest.class));
  }

  @Test
  @DisplayName("Deve criar usuario Cognito, sincronizar grupos e retornar perfil atualizado")
  void deveCriarUsuarioCognitoSincronizarGruposERetornarPerfilAtualizado() {
    CognitoUserProfile perfilSemGrupos =
        new CognitoUserProfile("ana@email.com", "ana@email.com", "Ana", "sub-1", Set.of());
    CognitoUserProfile perfilAtualizado =
        new CognitoUserProfile(
            "ana@email.com", "ana@email.com", "Ana", "sub-1", Set.of("CONTABIL"));

    when(cognitoRoleSyncService.normalizeGroup("CONTABIL")).thenReturn("CONTABIL");
    when(cognitoRoleSyncService.normalizeGroup("MANAGER")).thenReturn("MANAGER");
    when(cognitoIdentityProviderClient.listGroups(any(ListGroupsRequest.class)))
        .thenReturn(
            ListGroupsResponse.builder()
                .groups(
                    GroupType.builder().groupName("CONTABIL").build(),
                    GroupType.builder().groupName("MANAGER").build())
                .build());
    when(cognitoAuthProviderStrategy.loadProfile("ana@email.com"))
        .thenReturn(perfilSemGrupos)
        .thenReturn(perfilAtualizado);

    CognitoUserProfile resultado =
        cognitoUserManagementService.createUser(
            "Ana", "ana@email.com", "Senha@123", Set.of("CONTABIL"));

    assertEquals(perfilAtualizado, resultado);
    verify(cognitoIdentityProviderClient).adminCreateUser(any(AdminCreateUserRequest.class));
    verify(cognitoIdentityProviderClient)
        .adminSetUserPassword(any(AdminSetUserPasswordRequest.class));
    verify(cognitoIdentityProviderClient)
        .adminAddUserToGroup(any(AdminAddUserToGroupRequest.class));
    verify(cognitoIdentityProviderClient, never())
        .adminRemoveUserFromGroup(any(AdminRemoveUserFromGroupRequest.class));
  }

  @Test
  @DisplayName("Deve retornar conflito quando email ja existir no Cognito")
  void deveRetornarConflitoQuandoEmailJaExistirNoCognito() {
    Set<String> rolesSolicitadas = Set.of("CONTABIL");

    when(cognitoIdentityProviderClient.adminCreateUser(any(AdminCreateUserRequest.class)))
        .thenThrow(UsernameExistsException.builder().message("ja existe").build());

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () ->
                cognitoUserManagementService.createUser(
                    "Ana", "ana@email.com", "Senha@123", rolesSolicitadas));

    assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    assertEquals("Email ja cadastrado", ex.getReason());
  }

  @Test
  @DisplayName("Deve retornar bad request quando role nao tiver grupo Cognito correspondente")
  void deveRetornarBadRequestQuandoRoleNaoTiverGrupoCognitoCorrespondente() {
    Set<String> rolesSolicitadas = Set.of("ANDRE DO PRADO");

    when(cognitoRoleSyncService.normalizeGroup("ANDRE DO PRADO")).thenReturn("ANDRE DO PRADO");
    when(cognitoRoleSyncService.normalizeGroup("SUPPORT")).thenReturn("SUPPORT");
    when(cognitoIdentityProviderClient.listGroups(any(ListGroupsRequest.class)))
        .thenReturn(
            ListGroupsResponse.builder()
                .groups(GroupType.builder().groupName("SUPPORT").build())
                .build());

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () ->
                cognitoUserManagementService.updateUser(
                    "rafa@email.com", "Rafa", "rafa@email.com", null, rolesSolicitadas));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    assertEquals("Role sem grupo Cognito correspondente: ANDRE DO PRADO", ex.getReason());
  }

  @Test
  @DisplayName("Deve retornar not found quando perfil Cognito nao existir")
  void deveRetornarNotFoundQuandoPerfilCognitoNaoExistir() {
    when(cognitoAuthProviderStrategy.loadProfile("lucas@email.com"))
        .thenThrow(UserNotFoundException.builder().message("User does not exist.").build());

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> cognitoUserManagementService.findProfile("lucas@email.com"));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    assertEquals("Usuario nao encontrado no Cognito", ex.getReason());
  }

  @Test
  @DisplayName("Deve retornar not found ao atualizar usuario ausente no Cognito")
  void deveRetornarNotFoundAoAtualizarUsuarioAusenteNoCognito() {
    Set<String> rolesSolicitadas = Set.of("CONTABIL");

    when(cognitoIdentityProviderClient.adminUpdateUserAttributes(
            any(AdminUpdateUserAttributesRequest.class)))
        .thenThrow(UserNotFoundException.builder().message("User does not exist.").build());

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () ->
                cognitoUserManagementService.updateUser(
                    "lucas@email.com", "Lucas", "lucas@email.com", null, rolesSolicitadas));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    assertEquals("Usuario nao encontrado no Cognito", ex.getReason());
  }

  @Test
  @DisplayName("Deve retornar bad gateway especifico quando etapa de senha falhar no Cognito")
  void deveRetornarBadGatewayEspecificoQuandoEtapaDeSenhaFalharNoCognito() {
    Set<String> rolesSolicitadas = Set.of("CONTABIL");

    when(cognitoIdentityProviderClient.adminSetUserPassword(any(AdminSetUserPasswordRequest.class)))
        .thenThrow(new RuntimeException("boom"));

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () ->
                cognitoUserManagementService.updateUser(
                    "rafa@email.com", "Rafa", "rafa@email.com", "NovaSenha@123", rolesSolicitadas));

    assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    assertEquals("Falha ao atualizar senha no Cognito", ex.getReason());
  }
}
