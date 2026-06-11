package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.config.AuthProvider;
import com.sistema_contabilidade.auth.config.CognitoProperties;
import com.sistema_contabilidade.auth.dto.CompleteNewPasswordRequest;
import com.sistema_contabilidade.auth.dto.LoginRequest;
import com.sistema_contabilidade.auth.model.SessaoUsuario;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidParameterException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.TooManyRequestsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auth.provider", havingValue = "cognito")
public class CognitoAuthProviderStrategy implements AuthProviderStrategy {

  private final CognitoIdentityProviderClient cognitoIdentityProviderClient;
  private final CognitoProperties cognitoProperties;
  private final CognitoSecretHashService cognitoSecretHashService;
  private final SessaoUsuarioService sessaoUsuarioService;

  @Override
  public AuthProviderLoginResult login(LoginRequest request) {
    try {
      Map<String, String> params = new HashMap<>();
      params.put("USERNAME", request.email());
      params.put("PASSWORD", request.senha());
      cognitoSecretHashService.addSecretHashIfNeeded(params, request.email());

      AdminInitiateAuthResponse response =
          cognitoIdentityProviderClient.adminInitiateAuth(
              AdminInitiateAuthRequest.builder()
                  .userPoolId(cognitoProperties.getUserPoolId())
                  .clientId(cognitoProperties.getAppClientId())
                  .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                  .authParameters(params)
                  .build());
      if (response.challengeName() == ChallengeNameType.NEW_PASSWORD_REQUIRED) {
        return challengeResult(request.email(), response);
      }
      validateUnsupportedChallenge(response.challengeName());

      AuthenticationResultType authResult = response.authenticationResult();
      return authenticatedResult(request.email(), authResult);
    } catch (NotAuthorizedException | UserNotFoundException exception) {
      throw AuthFailureSupport.invalidCredentials(exception);
    } catch (TooManyRequestsException exception) {
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Limite de login excedido", exception);
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Falha ao autenticar no Cognito", exception);
    }
  }

  @Override
  public AuthProviderLoginResult completeNewPassword(
      AuthLoginChallenge challenge, CompleteNewPasswordRequest request) {
    if (!ChallengeNameType.NEW_PASSWORD_REQUIRED.toString().equals(challenge.challengeName())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Challenge de login invalido");
    }
    try {
      Map<String, String> params = new HashMap<>();
      params.put("USERNAME", challenge.providerUsername());
      params.put("NEW_PASSWORD", request.novaSenha());
      cognitoSecretHashService.addSecretHashIfNeeded(params, challenge.providerUsername());

      AdminRespondToAuthChallengeResponse response =
          cognitoIdentityProviderClient.adminRespondToAuthChallenge(
              AdminRespondToAuthChallengeRequest.builder()
                  .userPoolId(cognitoProperties.getUserPoolId())
                  .clientId(cognitoProperties.getAppClientId())
                  .challengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED)
                  .challengeResponses(params)
                  .session(challenge.challengeSession())
                  .build());
      validateUnsupportedChallenge(response.challengeName());
      return authenticatedResult(challenge.providerUsername(), response.authenticationResult());
    } catch (InvalidPasswordException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Nova senha nao atende a politica do Cognito", exception);
    } catch (InvalidParameterException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Solicitacao de troca inicial de senha invalida", exception);
    } catch (NotAuthorizedException exception) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Challenge Cognito expirado ou invalido", exception);
    } catch (TooManyRequestsException exception) {
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Limite de operacoes excedido", exception);
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Falha ao concluir troca inicial de senha no Cognito", exception);
    }
  }

  @Override
  public AuthProviderRefreshResult refresh(SessaoUsuario sessaoUsuario) {
    try {
      String refreshToken = sessaoUsuarioService.decryptRefreshToken(sessaoUsuario);
      if (refreshToken == null || refreshToken.isBlank()) {
        throw new ResponseStatusException(
            HttpStatus.UNAUTHORIZED, "Sessao sem refresh token Cognito");
      }

      Map<String, String> params = new HashMap<>();
      params.put("REFRESH_TOKEN", refreshToken);
      cognitoSecretHashService.addSecretHashIfNeeded(params, sessaoUsuario.getAuthUsername());

      InitiateAuthResponse response =
          cognitoIdentityProviderClient.initiateAuth(
              InitiateAuthRequest.builder()
                  .clientId(cognitoProperties.getAppClientId())
                  .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                  .authParameters(params)
                  .build());
      if (response.authenticationResult() == null) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh Cognito invalido");
      }

      CognitoUserProfile profile = loadProfile(sessaoUsuario.getAuthUsername());
      return new AuthProviderRefreshResult(
          AuthProvider.COGNITO,
          profile.providerUsername(),
          profile.email(),
          profile.nome(),
          profile.cognitoSub(),
          profile.groups(),
          null);
    } catch (NotAuthorizedException exception) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Refresh Cognito expirado", exception);
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Falha ao renovar sessao Cognito", exception);
    }
  }

  @Override
  public void logout(SessaoUsuario sessaoUsuario) {
    String username = sessaoUsuario.getAuthUsername();
    if (username == null || username.isBlank()) {
      return;
    }
    try {
      cognitoIdentityProviderClient.adminUserGlobalSignOut(
          AdminUserGlobalSignOutRequest.builder()
              .userPoolId(cognitoProperties.getUserPoolId())
              .username(username)
              .build());
    } catch (NotAuthorizedException exception) {
      log.debug("Usuario ja estava invalido no Cognito durante logout global", exception);
    }
  }

  @Override
  public boolean supports(AuthProvider provider) {
    return provider == AuthProvider.COGNITO;
  }

  public CognitoUserProfile loadProfile(String username) {
    AdminGetUserResponse userResponse =
        cognitoIdentityProviderClient.adminGetUser(
            AdminGetUserRequest.builder()
                .userPoolId(cognitoProperties.getUserPoolId())
                .username(username)
                .build());
    AdminListGroupsForUserResponse groupsResponse =
        cognitoIdentityProviderClient.adminListGroupsForUser(
            AdminListGroupsForUserRequest.builder()
                .userPoolId(cognitoProperties.getUserPoolId())
                .username(username)
                .build());

    String email = attribute(userResponse, "email");
    String nome =
        firstNonBlank(
            attribute(userResponse, "name"), attribute(userResponse, "given_name"), email);
    String cognitoSub = attribute(userResponse, "sub");
    Set<String> groups =
        groupsResponse.groups().stream()
            .map(GroupType::groupName)
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    return new CognitoUserProfile(userResponse.username(), email, nome, cognitoSub, groups);
  }

  private AuthProviderLoginResult challengeResult(
      String username, AdminInitiateAuthResponse response) {
    String challengeSession = response.session();
    if (challengeSession == null || challengeSession.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Cognito retornou challenge sem sessao temporaria");
    }
    String providerUsername = resolveChallengeUsername(response, username);
    return new AuthProviderLoginResult(
        AuthProvider.COGNITO,
        null,
        providerUsername,
        username,
        null,
        null,
        Set.of(),
        null,
        new AuthLoginChallenge(
            AuthProvider.COGNITO,
            ChallengeNameType.NEW_PASSWORD_REQUIRED.toString(),
            providerUsername,
            challengeSession,
            "Primeiro acesso detectado. Defina uma nova senha para continuar."));
  }

  private AuthProviderLoginResult authenticatedResult(
      String username, AuthenticationResultType authResult) {
    if (authResult == null
        || authResult.refreshToken() == null
        || authResult.refreshToken().isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Cognito nao retornou refresh token para a sessao");
    }

    CognitoUserProfile profile = loadProfile(username);
    return new AuthProviderLoginResult(
        AuthProvider.COGNITO,
        null,
        profile.providerUsername(),
        profile.email(),
        profile.nome(),
        profile.cognitoSub(),
        profile.groups(),
        authResult.refreshToken(),
        null);
  }

  private void validateUnsupportedChallenge(ChallengeNameType challengeName) {
    if (challengeName != null) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Cognito retornou challenge nao suportado: " + challengeName);
    }
  }

  private String attribute(AdminGetUserResponse response, String attributeName) {
    List<software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType> attributes =
        response.userAttributes();
    for (software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType attribute :
        attributes) {
      if (attributeName.equals(attribute.name())) {
        return attribute.value();
      }
    }
    return null;
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private String resolveChallengeUsername(AdminInitiateAuthResponse response, String fallback) {
    Map<String, String> challengeParameters = response.challengeParameters();
    if (challengeParameters == null || challengeParameters.isEmpty()) {
      return fallback;
    }
    return firstNonBlank(
        challengeParameters.get("USERNAME"), challengeParameters.get("USER_ID_FOR_SRP"), fallback);
  }
}
