package com.sistema_contabilidade.usuario.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PaginaUsuarioController {

  private static final String IS_AUTHENTICATED_EXPRESSION = "isAuthenticated()";

  @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<Resource> loginPage() {
    Resource resource = new ClassPathResource("static/login.html");
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(resource);
  }

  @GetMapping(value = "/404", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<Resource> notFoundPage() {
    Resource resource = new ClassPathResource("static/404.html");
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(resource);
  }

  @GetMapping(value = "/criar_usuario", produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Resource> criarUsuarioPage() {
    Resource resource = new ClassPathResource("static/criar_usuario.html");
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(resource);
  }

  @GetMapping(value = "/atualizar_usuario", produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Resource> atualizarUsuarioPage() {
    Resource resource = new ClassPathResource("static/atualizar_usuario.html");
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(resource);
  }

  @GetMapping(value = "/adicionar_comprovante", produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize(IS_AUTHENTICATED_EXPRESSION)
  public ResponseEntity<Resource> adicionarComprovantePage() {
    Resource resource = new ClassPathResource("static/adicionar_comprovante.html");
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(resource);
  }

  @GetMapping(value = "/home", produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize(IS_AUTHENTICATED_EXPRESSION)
  public ResponseEntity<Resource> homePage() {
    Resource resource = new ClassPathResource("static/home.html");
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(resource);
  }

  @GetMapping(value = "/lista_comprovantes", produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize(IS_AUTHENTICATED_EXPRESSION)
  public ResponseEntity<Resource> listaComprovantesPage() {
    Resource resource = new ClassPathResource("static/lista_comprovantes.html");
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(resource);
  }

  @GetMapping(value = "/relatorios", produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize(IS_AUTHENTICATED_EXPRESSION)
  public ResponseEntity<Resource> relatoriosPage() {
    Resource resource = new ClassPathResource("static/relatorios.html");
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(resource);
  }

  @GetMapping(value = "/relatorio_pdf", produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize(IS_AUTHENTICATED_EXPRESSION)
  public ResponseEntity<Resource> relatorioPdfPage() {
    Resource resource = new ClassPathResource("static/relatorio_pdf.html");
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(resource);
  }

  @GetMapping(value = "/admin", produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Resource> adminPage() {
    Resource resource = new ClassPathResource("static/admin.html");
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(resource);
  }
}
