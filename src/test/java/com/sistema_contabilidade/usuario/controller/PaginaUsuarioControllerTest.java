package com.sistema_contabilidade.usuario.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

@DisplayName("PaginaUsuarioController unit tests")
class PaginaUsuarioControllerTest {

  @Test
  @DisplayName("Deve retornar recurso html da pagina criar usuario")
  void deveRetornarRecursoHtmlDaPaginaCriarUsuario() {
    PaginaUsuarioController controller = new PaginaUsuarioController();

    ResponseEntity<Resource> response = controller.criarUsuarioPage();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("text/html", response.getHeaders().getContentType().toString());
  }

  @Test
  @DisplayName("Deve retornar recurso html da pagina adicionar comprovante")
  void deveRetornarRecursoHtmlDaPaginaAdicionarComprovante() {
    PaginaUsuarioController controller = new PaginaUsuarioController();

    ResponseEntity<Resource> response = controller.adicionarComprovantePage();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("text/html", response.getHeaders().getContentType().toString());
  }

  @Test
  @DisplayName("Deve exigir usuario autenticado para adicionar comprovante")
  void deveExigirUsuarioAutenticadoParaAdicionarComprovante() throws Exception {
    Method method = PaginaUsuarioController.class.getMethod("adicionarComprovantePage");

    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

    assertNotNull(preAuthorize);
    assertEquals("isAuthenticated()", preAuthorize.value());
  }
}
