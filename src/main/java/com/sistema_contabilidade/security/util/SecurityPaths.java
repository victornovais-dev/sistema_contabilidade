package com.sistema_contabilidade.security.util;

@SuppressWarnings("java:S1075")
public final class SecurityPaths {

  public static final String HTML_SUFFIX = ".html";
  public static final String API_V1_PREFIX = "/api/v1";
  public static final String ROOT_PATH = "/";
  public static final String LOGIN_PAGE = "/login";
  public static final String NOT_FOUND_PAGE = "/404";
  public static final String ERROR_PAGE = "/error";
  public static final String FAVICON_PATH = "/favicon.ico";
  public static final String ADMIN_PAGE = "/admin";
  public static final String ADMIN_PAGE_HTML = ADMIN_PAGE + HTML_SUFFIX;
  public static final String ID_PATH = "/{id}";
  public static final String ADD_RECEIPT_PAGE = "/adicionar_comprovante";
  public static final String ADD_RECEIPT_PAGE_HTML = ADD_RECEIPT_PAGE + HTML_SUFFIX;
  public static final String CREATE_USER_PAGE = "/criar_usuario";
  public static final String CREATE_USER_PAGE_HTML = CREATE_USER_PAGE + HTML_SUFFIX;
  public static final String MANAGE_ROLES_PAGE = "/gerenciar_roles";
  public static final String MANAGE_ROLES_PAGE_HTML = MANAGE_ROLES_PAGE + HTML_SUFFIX;
  public static final String UPDATE_USER_PAGE = "/atualizar_usuario";
  public static final String UPDATE_USER_PAGE_HTML = UPDATE_USER_PAGE + HTML_SUFFIX;
  public static final String NOTIFICATIONS_PAGE = "/notificacoes";
  public static final String NOTIFICATIONS_PAGE_HTML = NOTIFICATIONS_PAGE + HTML_SUFFIX;
  public static final String AUTH_API_BASE = API_V1_PREFIX + "/auth";
  public static final String USERS_API_BASE = API_V1_PREFIX + "/usuarios";
  public static final String USERS_ME_API_PATH = USERS_API_BASE + "/me";
  public static final String ADMIN_API_BASE = API_V1_PREFIX + ADMIN_PAGE;
  public static final String ITEMS_API_BASE = API_V1_PREFIX + "/itens";
  public static final String NOTIFICATIONS_API_PATTERN = API_V1_PREFIX + "/notificacoes/**";
  public static final String RELATORIOS_ROLES_API_PATH = API_V1_PREFIX + "/relatorios/roles";

  private SecurityPaths() {}
}
