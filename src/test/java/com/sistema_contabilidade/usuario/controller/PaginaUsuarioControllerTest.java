package com.sistema_contabilidade.usuario.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
}
