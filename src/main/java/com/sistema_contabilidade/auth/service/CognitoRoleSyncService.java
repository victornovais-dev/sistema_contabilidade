package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.config.CognitoProperties;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.usuario.model.Usuario;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auth.provider", havingValue = "cognito")
public class CognitoRoleSyncService {

  private final RoleRepository roleRepository;
  private final CognitoProperties cognitoProperties;

  public CognitoRoleSyncResult syncMemberships(Usuario usuario, Set<String> cognitoGroups) {
    Set<String> normalizedGroups = new LinkedHashSet<>();
    usuario.getRoles().clear();

    for (String group : cognitoGroups == null ? Set.<String>of() : cognitoGroups) {
      String normalized = normalizeGroup(group);
      normalizedGroups.add(normalized);
      Role role = findOrCreateRole(normalized);
      usuario.getRoles().add(role);
    }

    String groupsHash = hashGroups(normalizedGroups);
    usuario.setCognitoGroupsHash(groupsHash);
    usuario.setCognitoSyncedAt(LocalDateTime.now());
    return new CognitoRoleSyncResult(normalizedGroups, groupsHash);
  }

  public String hashGroups(Set<String> normalizedGroups) {
    try {
      String payload =
          normalizedGroups.stream()
              .filter(value -> value != null && !value.isBlank())
              .sorted(Comparator.naturalOrder())
              .reduce((left, right) -> left + "\n" + right)
              .orElse("");
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao gerar hash de grupos Cognito", exception);
    }
  }

  public String normalizeGroup(String group) {
    String value = group == null ? "" : group.trim();
    String groupPrefix = cognitoProperties.getGroupPrefix();
    if (groupPrefix != null && !groupPrefix.isBlank() && value.startsWith(groupPrefix)) {
      value = value.substring(groupPrefix.length());
    }
    value = value.replaceFirst("^ROLE_", "");
    value = value.replace('_', ' ').replace('-', ' ');
    value = value.replaceAll("\\s+", " ").trim();
    if (value.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Grupo Cognito invalido");
    }
    return value.toUpperCase(Locale.ROOT);
  }

  private Role findOrCreateRole(String normalizedRoleName) {
    return roleRepository
        .findByNomeIgnoreCase(normalizedRoleName)
        .orElseGet(() -> createMissingRole(normalizedRoleName));
  }

  private Role createMissingRole(String normalizedRoleName) {
    Role role = new Role();
    role.setNome(normalizedRoleName);
    try {
      return roleRepository.save(role);
    } catch (DataIntegrityViolationException exception) {
      return roleRepository
          .findByNomeIgnoreCase(normalizedRoleName)
          .orElseThrow(
              () ->
                  new ResponseStatusException(
                      HttpStatus.CONFLICT,
                      "Falha ao materializar role local para grupo Cognito: " + normalizedRoleName,
                      exception));
    }
  }
}
