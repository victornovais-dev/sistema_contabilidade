package com.sistema_contabilidade.usuario.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

@DisplayName("PaginaUsuarioController unit tests")
class PaginaUsuarioControllerTest {

  private static final String IS_AUTHENTICATED_EXPRESSION = "isAuthenticated()";
  private static final String ADMIN_EXPRESSION = "hasRole('ADMIN')";
  private static final String CONTENT_TYPE_HTML = "text/html";

  @ParameterizedTest(name = "Deve retornar recurso html da pagina {0}")
  @MethodSource("htmlPageMethods")
  void deveRetornarRecursoHtmlDasPaginas(String nomePagina, String methodName) throws Exception {
    PaginaUsuarioController controller = new PaginaUsuarioController();

    ResponseEntity<Resource> response = invokePageMethod(controller, methodName);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(CONTENT_TYPE_HTML, response.getHeaders().getContentType().toString());
  }

  @ParameterizedTest(name = "Deve exigir usuario autenticado para {0}")
  @MethodSource("authenticatedMethods")
  void deveExigirUsuarioAutenticadoNasPaginas(String nomePagina, String methodName)
      throws Exception {
    Method method = PaginaUsuarioController.class.getMethod(methodName);
    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

    assertNotNull(preAuthorize);
    assertEquals(IS_AUTHENTICATED_EXPRESSION, preAuthorize.value());
  }

  @ParameterizedTest(name = "Deve exigir role admin para {0}")
  @MethodSource("adminOnlyMethods")
  void deveExigirRoleAdminNasPaginas(String nomePagina, String methodName) throws Exception {
    Method method = PaginaUsuarioController.class.getMethod(methodName);
    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

    assertNotNull(preAuthorize);
    assertEquals(ADMIN_EXPRESSION, preAuthorize.value());
  }

  private static Stream<Arguments> htmlPageMethods() {
    return Stream.of(
        Arguments.of("login", "loginPage"),
        Arguments.of("criar usuario", "criarUsuarioPage"),
        Arguments.of("atualizar usuario", "atualizarUsuarioPage"),
        Arguments.of("adicionar comprovante", "adicionarComprovantePage"),
        Arguments.of("home", "homePage"),
        Arguments.of("lista comprovantes", "listaComprovantesPage"),
        Arguments.of("relatorios", "relatoriosPage"),
        Arguments.of("relatorio pdf", "relatorioPdfPage"),
        Arguments.of("admin", "adminPage"));
  }

  private static Stream<Arguments> authenticatedMethods() {
    return Stream.of(
        Arguments.of("adicionar comprovante", "adicionarComprovantePage"),
        Arguments.of("home", "homePage"),
        Arguments.of("lista comprovantes", "listaComprovantesPage"),
        Arguments.of("relatorios", "relatoriosPage"),
        Arguments.of("relatorio pdf", "relatorioPdfPage"));
  }

  private static Stream<Arguments> adminOnlyMethods() {
    return Stream.of(
        Arguments.of("criar usuario", "criarUsuarioPage"),
        Arguments.of("atualizar usuario", "atualizarUsuarioPage"),
        Arguments.of("admin", "adminPage"));
  }

  @SuppressWarnings("unchecked")
  private ResponseEntity<Resource> invokePageMethod(
      PaginaUsuarioController controller, String methodName) throws Exception {
    Method method = PaginaUsuarioController.class.getMethod(methodName);
    return (ResponseEntity<Resource>) method.invoke(controller);
  }
}
