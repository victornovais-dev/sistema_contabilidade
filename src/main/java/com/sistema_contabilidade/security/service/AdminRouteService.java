package com.sistema_contabilidade.security.service;

import com.sistema_contabilidade.security.util.SecurityPaths;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AdminRouteService {

  private static final String ADMIN_HASH_FALLBACK_SECRET = "admin-route-fallback-secret";
  private static final Pattern STATIC_RESOURCE_PATTERN =
      Pattern.compile("^/(assets|partials|css|js|images|webjars|actuator)(/.*)?$");

  private final String routeSecret;
  private final List<String> ipAllowlist;

  public AdminRouteService(
      @Value("${app.admin.route-secret:}") String routeSecret,
      @Value("${app.admin.ip-allowlist:}") String ipAllowlist) {
    this.routeSecret = routeSecret;
    this.ipAllowlist =
        Optional.ofNullable(ipAllowlist).stream()
            .flatMap(value -> List.of(value.split(",")).stream())
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
  }

  public String adminPagePath() {
    return secretPagePath(SecurityPaths.ADMIN_PAGE);
  }

  public String manageRolesPagePath() {
    return secretPagePath(SecurityPaths.MANAGE_ROLES_PAGE);
  }

  public String createUserPagePath() {
    return secretPagePath(SecurityPaths.CREATE_USER_PAGE);
  }

  public String updateUserPagePath() {
    return secretPagePath(SecurityPaths.UPDATE_USER_PAGE);
  }

  public String adminApiBasePath() {
    return SecurityPaths.API_V1_PREFIX + "/" + routeHash() + SecurityPaths.ADMIN_PAGE;
  }

  public String adminUserApiBasePath() {
    return SecurityPaths.API_V1_PREFIX + "/" + routeHash() + "/usuarios";
  }

  public Map<String, String> routeConfig() {
    Map<String, String> config = new LinkedHashMap<>();
    config.put("adminPagePath", adminPagePath());
    config.put("manageRolesPath", manageRolesPagePath());
    config.put("createUserPath", createUserPagePath());
    config.put("updateUserPath", updateUserPagePath());
    config.put("adminApiBasePath", adminApiBasePath());
    config.put("adminUserApiBasePath", adminUserApiBasePath());
    return Map.copyOf(config);
  }

  public Optional<String> resolveInternalPath(String requestPath) {
    if (Objects.equals(requestPath, adminPagePath())) {
      return Optional.of(SecurityPaths.ADMIN_PAGE);
    }
    if (Objects.equals(requestPath, manageRolesPagePath())) {
      return Optional.of(SecurityPaths.MANAGE_ROLES_PAGE);
    }
    if (Objects.equals(requestPath, createUserPagePath())) {
      return Optional.of(SecurityPaths.CREATE_USER_PAGE);
    }
    if (Objects.equals(requestPath, updateUserPagePath())) {
      return Optional.of(SecurityPaths.UPDATE_USER_PAGE);
    }
    if (requestPath.equals(adminApiBasePath())
        || requestPath.startsWith(adminApiBasePath() + "/")) {
      return Optional.of(
          requestPath.replaceFirst(
              "^" + Pattern.quote(adminApiBasePath()), SecurityPaths.ADMIN_API_BASE));
    }
    if (requestPath.equals(adminUserApiBasePath())
        || requestPath.startsWith(adminUserApiBasePath() + "/")) {
      return Optional.of(
          requestPath.replaceFirst(
              "^" + Pattern.quote(adminUserApiBasePath()), SecurityPaths.USERS_API_BASE));
    }
    return Optional.empty();
  }

  public boolean isLegacyAdminPath(String requestPath) {
    if (requestPath == null || requestPath.isBlank() || isStaticResource(requestPath)) {
      return false;
    }
    if (List.of(
            SecurityPaths.ADMIN_PAGE,
            SecurityPaths.ADMIN_PAGE_HTML,
            SecurityPaths.MANAGE_ROLES_PAGE,
            SecurityPaths.MANAGE_ROLES_PAGE_HTML,
            SecurityPaths.CREATE_USER_PAGE,
            SecurityPaths.CREATE_USER_PAGE_HTML,
            SecurityPaths.UPDATE_USER_PAGE,
            SecurityPaths.UPDATE_USER_PAGE_HTML)
        .contains(requestPath)) {
      return true;
    }
    if (SecurityPaths.ADMIN_API_BASE.equals(requestPath)
        || requestPath.startsWith(SecurityPaths.ADMIN_API_BASE + "/")) {
      return true;
    }
    if (SecurityPaths.USERS_API_BASE.equals(requestPath)
        || requestPath.startsWith(SecurityPaths.USERS_API_BASE + "/")) {
      return !SecurityPaths.USERS_ME_API_PATH.equals(requestPath)
          && !requestPath.startsWith(SecurityPaths.USERS_ME_API_PATH + "/");
    }
    return false;
  }

  public boolean isIpAllowed(String clientIp) {
    if (ipAllowlist.isEmpty()) {
      return true;
    }
    return ipAllowlist.stream().anyMatch(allowed -> Objects.equals(allowed, clientIp));
  }

  public boolean isStaticResource(String requestPath) {
    return requestPath != null
        && (STATIC_RESOURCE_PATTERN.matcher(requestPath).matches()
            || requestPath.startsWith("/favicon"));
  }

  private String secretPagePath(String suffix) {
    return "/" + routeHash() + suffix;
  }

  private String routeHash() {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      String secret =
          routeSecret == null || routeSecret.isBlank() ? ADMIN_HASH_FALLBACK_SECRET : routeSecret;
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(digest.digest(secret.getBytes(StandardCharsets.UTF_8)))
          .substring(0, 16);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Falha ao calcular rota admin secreta", exception);
    }
  }
}
