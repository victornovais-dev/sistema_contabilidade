package com.sistema_contabilidade.usuario.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PaginaUsuarioController {

  private static final String IS_AUTHENTICATED_EXPRESSION = "isAuthenticated()";
  private static final String ADMIN_ROLE_EXPRESSION = "hasRole('ADMIN')";
  private static final String NOTIFICATION_ROLE_EXPRESSION = "hasAnyRole('ADMIN','CONTABIL')";
  private static final String AUTHENTICATED_EXCEPT_CONTABIL_EXPRESSION =
      "isAuthenticated() and !hasRole('CONTABIL')";

  @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<Resource> loginPage() {
    Resource resource = new ClassPathResource("static/login.html");
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(resource);
  }

  @GetMapping(value = "/404", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<Resource> notFoundPage() {
    Resource resource = new ClassPathResource("static/404.html");
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.TEXT_HTML)
        .body(resource);
  }

  @GetMapping(value = "/criar_usuario", produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize(ADMIN_ROLE_EXPRESSION)
  public String criarUsuarioPage() {
    return "criar_usuario";
  }

  @GetMapping(value = "/atualizar_usuario", produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize(ADMIN_ROLE_EXPRESSION)
  public String atualizarUsuarioPage() {
    return "atualizar_usuario";
  }

  @GetMapping(value = "/adicionar_comprovante", produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize(AUTHENTICATED_EXCEPT_CONTABIL_EXPRESSION)
  public String adicionarComprovantePage() {
    return "adicionar_comprovante";
  }

  @GetMapping(value = "/home", produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize(IS_AUTHENTICATED_EXPRESSION)
  public String homePage() {
    return "home";
  }

  @GetMapping(value = "/lista_comprovantes", produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize(IS_AUTHENTICATED_EXPRESSION)
  public String listaComprovantesPage() {
    return "lista_comprovantes";
  }

  @GetMapping(value = "/relatorios", produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize(IS_AUTHENTICATED_EXPRESSION)
  public String relatoriosPage() {
    return "relatorios";
  }

  @GetMapping(value = "/notificacoes", produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize(NOTIFICATION_ROLE_EXPRESSION)
  public String notificacoesPage() {
    return "notificacoes";
  }

  @GetMapping(value = "/admin", produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize(ADMIN_ROLE_EXPRESSION)
  public String adminPage() {
    return "admin";
  }

  @GetMapping(value = "/gerenciar_roles", produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize(ADMIN_ROLE_EXPRESSION)
  public String gerenciarRolesPage() {
    return "gerenciar_roles";
  }
}
