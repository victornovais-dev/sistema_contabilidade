package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.config.CognitoProperties;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRemoveUserFromGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUpdateUserAttributesRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidParameterException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListGroupsRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListGroupsResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.TooManyRequestsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.auth.provider", havingValue = "cognito")
public class CognitoUserManagementService {

  private final CognitoIdentityProviderClient cognitoIdentityProviderClient;
  private final CognitoAuthProviderStrategy cognitoAuthProviderStrategy;
  private final CognitoProperties cognitoProperties;
  private final CognitoRoleSyncService cognitoRoleSyncService;

  public CognitoUserProfile createUser(
      String nome, String email, String senha, Set<String> normalizedRoles) {
    try {
      cognitoIdentityProviderClient.adminCreateUser(
          AdminCreateUserRequest.builder()
              .userPoolId(cognitoProperties.getUserPoolId())
              .username(email)
              .messageAction(MessageActionType.SUPPRESS)
              .userAttributes(userAttributes(nome, email))
              .build());
      cognitoIdentityProviderClient.adminSetUserPassword(
          AdminSetUserPasswordRequest.builder()
              .userPoolId(cognitoProperties.getUserPoolId())
              .username(email)
              .password(senha)
              .permanent(true)
              .build());
      syncGroups(email, normalizedRoles);
      return cognitoAuthProviderStrategy.loadProfile(email);
    } catch (UsernameExistsException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email ja cadastrado", exception);
    } catch (InvalidPasswordException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Senha nao atende a politica do Cognito", exception);
    } catch (InvalidParameterException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Solicitacao invalida ao Cognito", exception);
    } catch (TooManyRequestsException exception) {
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Limite de operacoes excedido no Cognito", exception);
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      log.error("Falha ao criar usuario no Cognito | email={}", email, exception);
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Falha ao criar usuario no Cognito", exception);
    }
  }

  public CognitoUserProfile updateUser(
      String providerUsername,
      String nome,
      String email,
      String senha,
      Set<String> normalizedRoles) {
    try {
      updateUserAttributes(providerUsername, nome, email);
      updatePasswordIfPresent(providerUsername, senha);
      syncGroupsIfPresent(providerUsername, normalizedRoles);
      return refreshUpdatedProfile(providerUsername);
    } catch (UserNotFoundException exception) {
      log.warn(
          "Usuario Cognito nao encontrado durante atualizacao administrativa | username={}",
          providerUsername);
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Usuario nao encontrado no Cognito", exception);
    } catch (InvalidPasswordException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Senha nao atende a politica do Cognito", exception);
    } catch (InvalidParameterException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Solicitacao invalida ao Cognito", exception);
    } catch (TooManyRequestsException exception) {
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Limite de operacoes excedido no Cognito", exception);
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      log.error(
          "Falha ao atualizar usuario no Cognito | username={} email={}",
          providerUsername,
          email,
          exception);
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Falha ao atualizar usuario no Cognito", exception);
    }
  }

  public CognitoUserProfile findProfile(String providerUsernameOrEmail) {
    try {
      return cognitoAuthProviderStrategy.loadProfile(providerUsernameOrEmail);
    } catch (UserNotFoundException exception) {
      log.warn(
          "Usuario Cognito nao encontrado durante consulta administrativa | username={}",
          providerUsernameOrEmail);
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Usuario nao encontrado no Cognito", exception);
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      log.error(
          "Falha ao consultar usuario no Cognito | username={}",
          providerUsernameOrEmail,
          exception);
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Falha ao consultar usuario no Cognito", exception);
    }
  }

  private void updateUserAttributes(String providerUsername, String nome, String email) {
    try {
      cognitoIdentityProviderClient.adminUpdateUserAttributes(
          AdminUpdateUserAttributesRequest.builder()
              .userPoolId(cognitoProperties.getUserPoolId())
              .username(providerUsername)
              .userAttributes(userAttributes(nome, email))
              .build());
    } catch (ResponseStatusException
        | UserNotFoundException
        | InvalidPasswordException
        | InvalidParameterException
        | TooManyRequestsException exception) {
      throw exception;
    } catch (Exception exception) {
      log.error(
          "Falha ao atualizar atributos no Cognito | username={} email={}",
          providerUsername,
          email,
          exception);
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Falha ao atualizar atributos no Cognito", exception);
    }
  }

  private void updatePasswordIfPresent(String providerUsername, String senha) {
    if (senha == null || senha.isBlank()) {
      return;
    }

    try {
      cognitoIdentityProviderClient.adminSetUserPassword(
          AdminSetUserPasswordRequest.builder()
              .userPoolId(cognitoProperties.getUserPoolId())
              .username(providerUsername)
              .password(senha)
              .permanent(true)
              .build());
    } catch (ResponseStatusException
        | UserNotFoundException
        | InvalidPasswordException
        | InvalidParameterException
        | TooManyRequestsException exception) {
      throw exception;
    } catch (Exception exception) {
      log.error(
          "Falha ao atualizar senha no Cognito | username={} senhaInformada=true",
          providerUsername,
          exception);
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Falha ao atualizar senha no Cognito", exception);
    }
  }

  private void syncGroupsIfPresent(String providerUsername, Set<String> normalizedRoles) {
    if (normalizedRoles == null) {
      return;
    }
    try {
      syncGroups(providerUsername, normalizedRoles);
    } catch (ResponseStatusException
        | UserNotFoundException
        | InvalidPasswordException
        | InvalidParameterException
        | TooManyRequestsException exception) {
      throw exception;
    } catch (Exception exception) {
      log.error(
          "Falha ao sincronizar grupos no Cognito | username={} roles={}",
          providerUsername,
          normalizedRoles,
          exception);
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Falha ao sincronizar grupos no Cognito", exception);
    }
  }

  private CognitoUserProfile refreshUpdatedProfile(String providerUsername) {
    try {
      return cognitoAuthProviderStrategy.loadProfile(providerUsername);
    } catch (ResponseStatusException
        | UserNotFoundException
        | InvalidPasswordException
        | InvalidParameterException
        | TooManyRequestsException exception) {
      throw exception;
    } catch (Exception exception) {
      log.error(
          "Falha ao consultar usuario atualizado no Cognito | username={}",
          providerUsername,
          exception);
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Falha ao consultar usuario atualizado no Cognito", exception);
    }
  }

  private void syncGroups(String providerUsername, Set<String> normalizedRoles) {
    Set<String> desiredGroups = resolveDesiredGroupNames(normalizedRoles);
    CognitoUserProfile currentProfile = cognitoAuthProviderStrategy.loadProfile(providerUsername);
    Set<String> currentGroups = new LinkedHashSet<>(currentProfile.groups());

    for (String group : currentGroups) {
      if (!desiredGroups.contains(group)) {
        cognitoIdentityProviderClient.adminRemoveUserFromGroup(
            AdminRemoveUserFromGroupRequest.builder()
                .userPoolId(cognitoProperties.getUserPoolId())
                .username(providerUsername)
                .groupName(group)
                .build());
      }
    }

    for (String group : desiredGroups) {
      if (!currentGroups.contains(group)) {
        cognitoIdentityProviderClient.adminAddUserToGroup(
            AdminAddUserToGroupRequest.builder()
                .userPoolId(cognitoProperties.getUserPoolId())
                .username(providerUsername)
                .groupName(group)
                .build());
      }
    }
  }

  private Set<String> resolveDesiredGroupNames(Set<String> normalizedRoles) {
    if (normalizedRoles == null || normalizedRoles.isEmpty()) {
      return Set.of();
    }

    Map<String, String> availableGroupNames = loadAvailableGroupNamesByNormalizedRole();
    Set<String> desiredGroups = new LinkedHashSet<>();
    for (String role : normalizedRoles) {
      String normalizedRole = cognitoRoleSyncService.normalizeGroup(role);
      String groupName = availableGroupNames.get(normalizedRole);
      if (groupName == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Role sem grupo Cognito correspondente: " + normalizedRole);
      }
      desiredGroups.add(groupName);
    }
    return desiredGroups;
  }

  private Map<String, String> loadAvailableGroupNamesByNormalizedRole() {
    Map<String, String> groupsByNormalizedRole = new java.util.LinkedHashMap<>();
    String nextToken = null;

    do {
      ListGroupsResponse response =
          cognitoIdentityProviderClient.listGroups(
              ListGroupsRequest.builder()
                  .userPoolId(cognitoProperties.getUserPoolId())
                  .nextToken(nextToken)
                  .build());
      for (GroupType group : response.groups()) {
        if (group == null || group.groupName() == null || group.groupName().isBlank()) {
          continue;
        }
        groupsByNormalizedRole.put(
            cognitoRoleSyncService.normalizeGroup(group.groupName()), group.groupName());
      }
      nextToken = response.nextToken();
    } while (nextToken != null && !nextToken.isBlank());

    return groupsByNormalizedRole;
  }

  private List<AttributeType> userAttributes(String nome, String email) {
    return List.of(
        AttributeType.builder().name("email").value(email).build(),
        AttributeType.builder().name("email_verified").value("true").build(),
        AttributeType.builder().name("name").value(nome).build());
  }
}
