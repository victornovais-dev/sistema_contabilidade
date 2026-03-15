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

  @GetMapping(value = "/criar_usuario", produces = MediaType.TEXT_HTML_VALUE)
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Resource> criarUsuarioPage() {
    Resource resource = new ClassPathResource("static/criar_usuario.html");
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(resource);
  }
}
