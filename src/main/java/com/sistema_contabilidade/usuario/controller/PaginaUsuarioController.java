package com.sistema_contabilidade.usuario.controller;

import com.sistema_contabilidade.security.util.SecurityPaths;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PaginaUsuarioController {

  private static final String IS_AUTHENTICATED_EXPRESSION = "isAuthenticated()";
  private static final String ADMIN_ROLE_EXPRESSION = "hasRole('ADMIN')";
  private static final String CONTABIL_ROLE_EXPRESSION = "hasRole('CONTABIL')";
  private static final String NOTIFICATION_ROLE_EXPRESSION =
      "hasAnyRole('ADMIN','CONTABIL','ESTAGIARIO')";
  private static final String AUTHENTICATED_EXCEPT_CONTABIL_EXPRESSION =
      "isAuthenticated() and !hasAnyRole('CONTABIL','ESTAGIARIO')";

  @GetMapping(SecurityPaths.ROOT_PATH)
  public String rootPage(Authentication authentication) {
    return isAuthenticated(authentication)
        ? "redirect:" + SecurityPaths.ROOT_PATH + "home"
        : "redirect:" + SecurityPaths.LOGIN_PAGE;
  }

  @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<Resource> loginPage() {
    Resource resource = new ClassPathResource("static/login.html");
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(resource);
  }

  @GetMapping(value = SecurityPaths.FIRST_ACCESS_PAGE, produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<Resource> firstAccessPage() {
    Resource resource = new ClassPathResource("static/primeiro_acesso.html");
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(resource);
  }

  @GetMapping(value = SecurityPaths.PUBLIC_INFO_PAGE, produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<Resource> publicInfoPage() {
    Resource resource = new ClassPathResource("static/conheca.html");
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(resource);
  }

  @GetMapping(value = SecurityPaths.PUBLIC_PRIVACY_PAGE, produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<Resource> publicPrivacyPage() {
    Resource resource = new ClassPathResource("static/privacidade.html");
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

  @GetMapping(value = SecurityPaths.MANAGE_INTERNS_PAGE, produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize(CONTABIL_ROLE_EXPRESSION)
  public String gerenciarEstagiariosPage() {
    return "gerenciar_estagiarios";
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

  @GetMapping(value = SecurityPaths.QUESTIONS_PAGE, produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize(ADMIN_ROLE_EXPRESSION)
  public String duvidasPage() {
    return "duvidas";
  }

  @GetMapping(value = SecurityPaths.PRIVACY_REQUESTS_PAGE, produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize(ADMIN_ROLE_EXPRESSION)
  public String solicitacoesTitularesPage() {
    return "solicitacoes_titulares";
  }

  @GetMapping(value = "/gerenciar_roles", produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize(ADMIN_ROLE_EXPRESSION)
  public String gerenciarRolesPage() {
    return "gerenciar_roles";
  }

  private boolean isAuthenticated(Authentication authentication) {
    return authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken);
  }
}
