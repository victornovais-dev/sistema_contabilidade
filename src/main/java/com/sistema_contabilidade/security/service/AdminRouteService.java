package com.sistema_contabilidade.security.service;

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

  private static final String API_PREFIX = "/api/v1";
  private static final String ADMIN_PAGE_PATH = "/admin";
  private static final String ADMIN_PAGE_HTML_PATH = "/admin.html";
  private static final String MANAGE_ROLES_PATH = "/gerenciar_roles";
  private static final String MANAGE_ROLES_HTML_PATH = "/gerenciar_roles.html";
  private static final String CREATE_USER_PATH = "/criar_usuario";
  private static final String CREATE_USER_HTML_PATH = "/criar_usuario.html";
  private static final String UPDATE_USER_PATH = "/atualizar_usuario";
  private static final String UPDATE_USER_HTML_PATH = "/atualizar_usuario.html";
  private static final String ADMIN_API_PATH = API_PREFIX + "/admin";
  private static final String USERS_API_PATH = API_PREFIX + "/usuarios";
  private static final String CURRENT_USER_API_PATH = USERS_API_PATH + "/me";
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
    return secretPagePath(ADMIN_PAGE_PATH);
  }

  public String manageRolesPagePath() {
    return secretPagePath(MANAGE_ROLES_PATH);
  }

  public String createUserPagePath() {
    return secretPagePath(CREATE_USER_PATH);
  }

  public String updateUserPagePath() {
    return secretPagePath(UPDATE_USER_PATH);
  }

  public String adminApiBasePath() {
    return API_PREFIX + "/" + routeHash() + "/admin";
  }

  public String adminUserApiBasePath() {
    return API_PREFIX + "/" + routeHash() + "/usuarios";
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
      return Optional.of(ADMIN_PAGE_PATH);
    }
    if (Objects.equals(requestPath, manageRolesPagePath())) {
      return Optional.of(MANAGE_ROLES_PATH);
    }
    if (Objects.equals(requestPath, createUserPagePath())) {
      return Optional.of(CREATE_USER_PATH);
    }
    if (Objects.equals(requestPath, updateUserPagePath())) {
      return Optional.of(UPDATE_USER_PATH);
    }
    if (requestPath.equals(adminApiBasePath())
        || requestPath.startsWith(adminApiBasePath() + "/")) {
      return Optional.of(
          requestPath.replaceFirst("^" + Pattern.quote(adminApiBasePath()), ADMIN_API_PATH));
    }
    if (requestPath.equals(adminUserApiBasePath())
        || requestPath.startsWith(adminUserApiBasePath() + "/")) {
      return Optional.of(
          requestPath.replaceFirst("^" + Pattern.quote(adminUserApiBasePath()), USERS_API_PATH));
    }
    return Optional.empty();
  }

  public boolean isLegacyAdminPath(String requestPath) {
    if (requestPath == null || requestPath.isBlank() || isStaticResource(requestPath)) {
      return false;
    }
    if (List.of(
            ADMIN_PAGE_PATH,
            ADMIN_PAGE_HTML_PATH,
            MANAGE_ROLES_PATH,
            MANAGE_ROLES_HTML_PATH,
            CREATE_USER_PATH,
            CREATE_USER_HTML_PATH,
            UPDATE_USER_PATH,
            UPDATE_USER_HTML_PATH)
        .contains(requestPath)) {
      return true;
    }
    if (ADMIN_API_PATH.equals(requestPath) || requestPath.startsWith(ADMIN_API_PATH + "/")) {
      return true;
    }
    if (USERS_API_PATH.equals(requestPath) || requestPath.startsWith(USERS_API_PATH + "/")) {
      return !CURRENT_USER_API_PATH.equals(requestPath)
          && !requestPath.startsWith(CURRENT_USER_API_PATH + "/");
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
