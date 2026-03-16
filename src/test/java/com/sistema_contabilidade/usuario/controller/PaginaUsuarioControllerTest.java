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

  private static final String IS_AUTHENTICATED_EXPRESSION = "isAuthenticated()";

  @Test
  @DisplayName("Deve retornar recurso html da pagina login")
  void deveRetornarRecursoHtmlDaPaginaLogin() {
    PaginaUsuarioController controller = new PaginaUsuarioController();

    ResponseEntity<Resource> response = controller.loginPage();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("text/html", response.getHeaders().getContentType().toString());
  }

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

  @Test
  @DisplayName("Deve retornar recurso html da pagina home")
  void deveRetornarRecursoHtmlDaPaginaHome() {
    PaginaUsuarioController controller = new PaginaUsuarioController();

    ResponseEntity<Resource> response = controller.homePage();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("text/html", response.getHeaders().getContentType().toString());
  }

  @Test
  @DisplayName("Deve exigir usuario autenticado para home")
  void deveExigirUsuarioAutenticadoParaHome() throws Exception {
    Method method = PaginaUsuarioController.class.getMethod("homePage");

    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

    assertNotNull(preAuthorize);
    assertEquals(IS_AUTHENTICATED_EXPRESSION, preAuthorize.value());
  }

  @Test
  @DisplayName("Deve retornar recurso html da pagina lista comprovantes")
  void deveRetornarRecursoHtmlDaPaginaListaComprovantes() {
    PaginaUsuarioController controller = new PaginaUsuarioController();

    ResponseEntity<Resource> response = controller.listaComprovantesPage();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("text/html", response.getHeaders().getContentType().toString());
  }

  @Test
  @DisplayName("Deve exigir usuario autenticado para lista comprovantes")
  void deveExigirUsuarioAutenticadoParaListaComprovantes() throws Exception {
    Method method = PaginaUsuarioController.class.getMethod("listaComprovantesPage");

    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

    assertNotNull(preAuthorize);
    assertEquals(IS_AUTHENTICATED_EXPRESSION, preAuthorize.value());
  }

  @Test
  @DisplayName("Deve retornar recurso html da pagina relatorios")
  void deveRetornarRecursoHtmlDaPaginaRelatorios() {
    PaginaUsuarioController controller = new PaginaUsuarioController();

    ResponseEntity<Resource> response = controller.relatoriosPage();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("text/html", response.getHeaders().getContentType().toString());
  }

  @Test
  @DisplayName("Deve exigir usuario autenticado para relatorios")
  void deveExigirUsuarioAutenticadoParaRelatorios() throws Exception {
    Method method = PaginaUsuarioController.class.getMethod("relatoriosPage");

    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

    assertNotNull(preAuthorize);
    assertEquals(IS_AUTHENTICATED_EXPRESSION, preAuthorize.value());
  }

  @Test
  @DisplayName("Deve retornar recurso html da pagina relatorio pdf")
  void deveRetornarRecursoHtmlDaPaginaRelatorioPdf() {
    PaginaUsuarioController controller = new PaginaUsuarioController();

    ResponseEntity<Resource> response = controller.relatorioPdfPage();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("text/html", response.getHeaders().getContentType().toString());
  }

  @Test
  @DisplayName("Deve exigir usuario autenticado para relatorio pdf")
  void deveExigirUsuarioAutenticadoParaRelatorioPdf() throws Exception {
    Method method = PaginaUsuarioController.class.getMethod("relatorioPdfPage");

    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

    assertNotNull(preAuthorize);
    assertEquals(IS_AUTHENTICATED_EXPRESSION, preAuthorize.value());
  }

  @Test
  @DisplayName("Deve exigir role admin para criar usuario")
  void deveExigirRoleAdminParaCriarUsuario() throws Exception {
    Method method = PaginaUsuarioController.class.getMethod("criarUsuarioPage");

    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

    assertNotNull(preAuthorize);
    assertEquals("hasRole('ADMIN')", preAuthorize.value());
  }

  @Test
  @DisplayName("Deve retornar recurso html da pagina admin")
  void deveRetornarRecursoHtmlDaPaginaAdmin() {
    PaginaUsuarioController controller = new PaginaUsuarioController();

    ResponseEntity<Resource> response = controller.adminPage();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("text/html", response.getHeaders().getContentType().toString());
  }

  @Test
  @DisplayName("Deve exigir role admin para pagina admin")
  void deveExigirRoleAdminParaPaginaAdmin() throws Exception {
    Method method = PaginaUsuarioController.class.getMethod("adminPage");

    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

    assertNotNull(preAuthorize);
    assertEquals("hasRole('ADMIN')", preAuthorize.value());
  }
}
