package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.config.CognitoProperties;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListGroupsRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListGroupsResponse;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auth.provider", havingValue = "cognito")
public class CognitoGroupCatalogService {

  private final CognitoIdentityProviderClient cognitoIdentityProviderClient;
  private final CognitoProperties cognitoProperties;
  private final CognitoRoleSyncService cognitoRoleSyncService;

  public List<String> listNormalizedGroups() {
    return listNormalizedGroups(group -> true);
  }

  public List<String> listCandidateNormalizedGroups() {
    return listNormalizedGroups(this::isCandidateGroup);
  }

  public List<String> listCandidateNormalizedGroupsForUser(String email, boolean isAdmin) {
    return listNormalizedGroups(
        group -> isCandidateGroup(group) && (isAdmin || isAvailableForEmail(group, email)));
  }

  private List<String> listNormalizedGroups(Predicate<GroupType> filter) {
    try {
      Set<String> normalizedGroups = new LinkedHashSet<>();
      String nextToken = null;

      do {
        ListGroupsResponse response =
            cognitoIdentityProviderClient.listGroups(
                ListGroupsRequest.builder()
                    .userPoolId(cognitoProperties.getUserPoolId())
                    .nextToken(nextToken)
                    .build());
        for (GroupType group : response.groups()) {
          if (group == null
              || group.groupName() == null
              || group.groupName().isBlank()
              || !filter.test(group)) {
            continue;
          }
          normalizedGroups.add(cognitoRoleSyncService.normalizeGroup(group.groupName()));
        }
        nextToken = response.nextToken();
      } while (nextToken != null && !nextToken.isBlank());

      List<String> orderedGroups = new ArrayList<>(normalizedGroups);
      orderedGroups.sort(String.CASE_INSENSITIVE_ORDER);
      return orderedGroups;
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Falha ao carregar grupos do Cognito", exception);
    }
  }

  private boolean isCandidateGroup(GroupType group) {
    if (group == null || group.description() == null || group.description().isBlank()) {
      return false;
    }
    return descriptionParts(group).stream().anyMatch("CANDIDATO"::equals);
  }

  private boolean isAvailableForEmail(GroupType group, String email) {
    String restrictedDomain = restrictedDomain(group);
    if (restrictedDomain == null) {
      return true;
    }
    if (restrictedDomain.isBlank() || email == null || email.isBlank()) {
      return false;
    }
    return email.trim().toLowerCase(Locale.ROOT).endsWith("@" + restrictedDomain);
  }

  private String restrictedDomain(GroupType group) {
    for (String part : descriptionParts(group)) {
      int separator = part.indexOf('=');
      if (separator >= 0 && "DOMINIO".equals(part.substring(0, separator).trim())) {
        return part.substring(separator + 1).trim().toLowerCase(Locale.ROOT).replaceFirst("^@", "");
      }
    }
    return null;
  }

  private List<String> descriptionParts(GroupType group) {
    if (group == null || group.description() == null || group.description().isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(group.description().split(";"))
        .map(String::trim)
        .filter(part -> !part.isBlank())
        .map(part -> part.toUpperCase(Locale.ROOT))
        .toList();
  }
}
