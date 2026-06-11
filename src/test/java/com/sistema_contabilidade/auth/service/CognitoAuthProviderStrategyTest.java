package com.sistema_contabilidade.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.auth.config.AuthProvider;
import com.sistema_contabilidade.auth.config.CognitoProperties;
import com.sistema_contabilidade.auth.dto.CompleteNewPasswordRequest;
import com.sistema_contabilidade.auth.dto.LoginRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRespondToAuthChallengeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRespondToAuthChallengeResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType;

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
