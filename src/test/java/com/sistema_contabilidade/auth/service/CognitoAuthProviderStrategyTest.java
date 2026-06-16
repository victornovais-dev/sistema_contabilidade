package com.sistema_contabilidade.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.auth.config.AuthProvider;
import com.sistema_contabilidade.auth.config.CognitoProperties;
import com.sistema_contabilidade.auth.dto.CompleteNewPasswordRequest;
import com.sistema_contabilidade.auth.dto.LoginRequest;
import com.sistema_contabilidade.auth.model.SessaoUsuario;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRespondToAuthChallengeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRespondToAuthChallengeResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUserGlobalSignOutRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;

@ExtendWith(MockitoExtension.class)
@DisplayName("CognitoAuthProviderStrategy unit tests")
class CognitoAuthProviderStrategyTest {

  @Mock private CognitoIdentityProviderClient cognitoIdentityProviderClient;
  @Mock private CognitoSecretHashService cognitoSecretHashService;
  @Mock private SessaoUsuarioService sessaoUsuarioService;

  @Test
  @DisplayName("Deve retornar challenge de nova senha quando Cognito exigir primeiro acesso")
  void deveRetornarChallengeDeNovaSenhaQuandoCognitoExigirPrimeiroAcesso() {
    CognitoAuthProviderStrategy service = service();
    doNothing().when(cognitoSecretHashService).addSecretHashIfNeeded(any(), any());
    when(cognitoIdentityProviderClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
        .thenReturn(
            AdminInitiateAuthResponse.builder()
                .challengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED)
                .session("challenge-session")
                .challengeParameters(java.util.Map.of("USERNAME", "cognito-username"))
                .build());

    AuthProviderLoginResult result = service.login(new LoginRequest("ana@email.com", "Temp@123"));

    assertNotNull(result.challenge());
    assertEquals("NEW_PASSWORD_REQUIRED", result.challenge().challengeName());
    assertEquals(AuthProvider.COGNITO, result.challenge().provider());
    assertEquals("cognito-username", result.challenge().providerUsername());
  }

  @Test
  @DisplayName("Deve concluir nova senha e retornar autenticacao Cognito")
  void deveConcluirNovaSenhaERetornarAutenticacaoCognito() {
    CognitoAuthProviderStrategy service = service();
    doNothing().when(cognitoSecretHashService).addSecretHashIfNeeded(any(), any());
    when(cognitoIdentityProviderClient.adminRespondToAuthChallenge(
            any(AdminRespondToAuthChallengeRequest.class)))
        .thenReturn(
            AdminRespondToAuthChallengeResponse.builder()
                .authenticationResult(
                    AuthenticationResultType.builder().refreshToken("refresh-token").build())
                .build());
    when(cognitoIdentityProviderClient.adminGetUser(any(AdminGetUserRequest.class)))
        .thenReturn(
            AdminGetUserResponse.builder()
                .username("ana@email.com")
                .userAttributes(
                    List.of(
                        AttributeType.builder().name("email").value("ana@email.com").build(),
                        AttributeType.builder().name("name").value("Ana").build(),
                        AttributeType.builder().name("sub").value("sub-123").build()))
                .build());
    when(cognitoIdentityProviderClient.adminListGroupsForUser(
            any(AdminListGroupsForUserRequest.class)))
        .thenReturn(
            AdminListGroupsForUserResponse.builder()
                .groups(List.of(GroupType.builder().groupName("ADMIN").build()))
                .build());

    AuthProviderLoginResult result =
        service.completeNewPassword(
            new AuthLoginChallenge(
                AuthProvider.COGNITO,
                "NEW_PASSWORD_REQUIRED",
                "cognito-username",
                "challenge-session",
                "Primeiro acesso detectado. Defina uma nova senha para continuar."),
            new CompleteNewPasswordRequest("Nova@123"));

    assertEquals("refresh-token", result.refreshToken());
    assertEquals("sub-123", result.cognitoSub());
    assertEquals("ana@email.com", result.email());
    assertEquals("ADMIN", result.groups().iterator().next());
    ArgumentCaptor<AdminRespondToAuthChallengeRequest> requestCaptor =
        ArgumentCaptor.forClass(AdminRespondToAuthChallengeRequest.class);
    verify(cognitoIdentityProviderClient).adminRespondToAuthChallenge(requestCaptor.capture());
    assertEquals("cognito-username", requestCaptor.getValue().challengeResponses().get("USERNAME"));
  }

  @Test
  @DisplayName("Deve autenticar usuario quando Cognito retornar refresh token")
  void deveAutenticarUsuarioQuandoCognitoRetornarRefreshToken() {
    CognitoAuthProviderStrategy service = service();
    doNothing().when(cognitoSecretHashService).addSecretHashIfNeeded(any(), any());
    when(cognitoIdentityProviderClient.adminInitiateAuth(any(AdminInitiateAuthRequest.class)))
        .thenReturn(
            AdminInitiateAuthResponse.builder()
                .authenticationResult(
                    AuthenticationResultType.builder().refreshToken("refresh-login").build())
                .build());
    when(cognitoIdentityProviderClient.adminGetUser(any(AdminGetUserRequest.class)))
        .thenReturn(
            AdminGetUserResponse.builder()
                .username("cognito-ana")
                .userAttributes(
                    List.of(
                        AttributeType.builder().name("email").value("ana@email.com").build(),
                        AttributeType.builder().name("given_name").value("Ana").build(),
                        AttributeType.builder().name("sub").value("sub-login").build()))
                .build());
    when(cognitoIdentityProviderClient.adminListGroupsForUser(
            any(AdminListGroupsForUserRequest.class)))
        .thenReturn(
            AdminListGroupsForUserResponse.builder()
                .groups(
                    List.of(
                        GroupType.builder().groupName("ADMIN").build(),
                        GroupType.builder().groupName("SUPPORT").build()))
                .build());

    AuthProviderLoginResult result = service.login(new LoginRequest("ana@email.com", "Senha@123"));

    assertEquals(AuthProvider.COGNITO, result.provider());
    assertEquals("refresh-login", result.refreshToken());
    assertEquals("cognito-ana", result.providerUsername());
    assertEquals("ana@email.com", result.email());
    assertEquals("Ana", result.nome());
    assertEquals("sub-login", result.cognitoSub());
    assertEquals(Set.of("ADMIN", "SUPPORT"), result.groups());
  }

  @Test
  @DisplayName("Deve rejeitar challenge invalido ao concluir nova senha")
  void deveRejeitarChallengeInvalidoAoConcluirNovaSenha() {
    CognitoAuthProviderStrategy service = service();
    AuthLoginChallenge challengeInvalido =
        new AuthLoginChallenge(
            AuthProvider.COGNITO, "SMS_MFA", "cognito-username", "challenge-session", "Mensagem");
    CompleteNewPasswordRequest request = new CompleteNewPasswordRequest("Nova@123");

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> service.completeNewPassword(challengeInvalido, request));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    verify(cognitoIdentityProviderClient, never())
        .adminRespondToAuthChallenge(any(AdminRespondToAuthChallengeRequest.class));
  }

  @Test
  @DisplayName("Deve renovar sessao Cognito com refresh token valido")
  void deveRenovarSessaoCognitoComRefreshTokenValido() {
    CognitoAuthProviderStrategy service = service();
    SessaoUsuario sessaoUsuario = new SessaoUsuario();
    sessaoUsuario.setAuthUsername("ana@email.com");
    when(sessaoUsuarioService.decryptRefreshToken(sessaoUsuario)).thenReturn("refresh-token");
    doNothing().when(cognitoSecretHashService).addSecretHashIfNeeded(any(), any());
    when(cognitoIdentityProviderClient.initiateAuth(any(InitiateAuthRequest.class)))
        .thenReturn(
            InitiateAuthResponse.builder()
                .authenticationResult(AuthenticationResultType.builder().build())
                .build());
    when(cognitoIdentityProviderClient.adminGetUser(any(AdminGetUserRequest.class)))
        .thenReturn(
            AdminGetUserResponse.builder()
                .username("ana@email.com")
                .userAttributes(
                    List.of(
                        AttributeType.builder().name("email").value("ana@email.com").build(),
                        AttributeType.builder().name("name").value("Ana Atualizada").build(),
                        AttributeType.builder().name("sub").value("sub-refresh").build()))
                .build());
    when(cognitoIdentityProviderClient.adminListGroupsForUser(
            any(AdminListGroupsForUserRequest.class)))
        .thenReturn(
            AdminListGroupsForUserResponse.builder()
                .groups(List.of(GroupType.builder().groupName("CONTABIL").build()))
                .build());

    AuthProviderRefreshResult result = service.refresh(sessaoUsuario);

    assertEquals(AuthProvider.COGNITO, result.provider());
    assertEquals("ana@email.com", result.providerUsername());
    assertEquals("ana@email.com", result.email());
    assertEquals("Ana Atualizada", result.nome());
    assertEquals("sub-refresh", result.cognitoSub());
    assertEquals(Set.of("CONTABIL"), result.groups());
  }

  @Test
  @DisplayName("Deve rejeitar refresh Cognito sem token")
  void deveRejeitarRefreshCognitoSemToken() {
    CognitoAuthProviderStrategy service = service();
    SessaoUsuario sessaoUsuario = new SessaoUsuario();
    sessaoUsuario.setAuthUsername("ana@email.com");
    when(sessaoUsuarioService.decryptRefreshToken(sessaoUsuario)).thenReturn(" ");

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> service.refresh(sessaoUsuario));

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    assertEquals("Sessao sem refresh token Cognito", exception.getReason());
  }

  @Test
  @DisplayName("Deve rejeitar refresh Cognito sem resultado de autenticacao")
  void deveRejeitarRefreshCognitoSemResultadoDeAutenticacao() {
    CognitoAuthProviderStrategy service = service();
    SessaoUsuario sessaoUsuario = new SessaoUsuario();
    sessaoUsuario.setAuthUsername("ana@email.com");
    when(sessaoUsuarioService.decryptRefreshToken(sessaoUsuario)).thenReturn("refresh-token");
    doNothing().when(cognitoSecretHashService).addSecretHashIfNeeded(any(), any());
    when(cognitoIdentityProviderClient.initiateAuth(any(InitiateAuthRequest.class)))
        .thenReturn(InitiateAuthResponse.builder().build());

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> service.refresh(sessaoUsuario));

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    assertEquals("Refresh Cognito invalido", exception.getReason());
  }

  @Test
  @DisplayName("Deve ignorar logout Cognito quando username nao estiver preenchido")
  void deveIgnorarLogoutCognitoQuandoUsernameNaoEstiverPreenchido() {
    CognitoAuthProviderStrategy service = service();
    SessaoUsuario sessaoUsuario = new SessaoUsuario();
    sessaoUsuario.setAuthUsername(" ");

    service.logout(sessaoUsuario);

    verify(cognitoIdentityProviderClient, never())
        .adminUserGlobalSignOut(any(AdminUserGlobalSignOutRequest.class));
  }

  @Test
  @DisplayName("Deve ignorar erro de logout quando Cognito informar usuario invalido")
  void deveIgnorarErroDeLogoutQuandoCognitoInformarUsuarioInvalido() {
    CognitoAuthProviderStrategy service = service();
    SessaoUsuario sessaoUsuario = new SessaoUsuario();
    sessaoUsuario.setAuthUsername("ana@email.com");
    when(cognitoIdentityProviderClient.adminUserGlobalSignOut(
            any(AdminUserGlobalSignOutRequest.class)))
        .thenThrow(NotAuthorizedException.builder().message("invalid").build());

    service.logout(sessaoUsuario);

    verify(cognitoIdentityProviderClient)
        .adminUserGlobalSignOut(any(AdminUserGlobalSignOutRequest.class));
  }

  private CognitoAuthProviderStrategy service() {
    CognitoProperties cognitoProperties = new CognitoProperties();
    cognitoProperties.setUserPoolId("us-east-1_pool");
    cognitoProperties.setAppClientId("client-id");
    return new CognitoAuthProviderStrategy(
        cognitoIdentityProviderClient,
        cognitoProperties,
        cognitoSecretHashService,
        sessaoUsuarioService);
  }
}
