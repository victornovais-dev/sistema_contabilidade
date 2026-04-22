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
  private static final String AUTHENTICATED_EXCEPT_CONTABIL_EXPRESSION =
      "isAuthenticated() and !hasRole('CONTABIL')";
  private static final String ADMIN_EXPRESSION = "hasRole('ADMIN')";
  private static final String NOTIFICATION_EXPRESSION = "hasAnyRole('ADMIN','CONTABIL')";
  private static final String CONTENT_TYPE_HTML = "text/html";

  @ParameterizedTest(name = "Deve retornar recurso html da pagina {0}")
  @MethodSource("resourcePageMethods")
  void deveRetornarRecursoHtmlDasPaginas(
      String nomePagina, String methodName, HttpStatus expectedStatus) throws Exception {
    PaginaUsuarioController controller = new PaginaUsuarioController();

    ResponseEntity<Resource> response = invokePageMethod(controller, methodName);

    assertEquals(expectedStatus, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(CONTENT_TYPE_HTML, response.getHeaders().getContentType().toString());
  }

  @ParameterizedTest(name = "Deve retornar nome de template da pagina {0}")
  @MethodSource("templatePageMethods")
  void deveRetornarNomeDoTemplateDasPaginas(
      String nomePagina, String methodName, String templateName) throws Exception {
    PaginaUsuarioController controller = new PaginaUsuarioController();

    String response = invokeTemplatePageMethod(controller, methodName);

    assertEquals(templateName, response);
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

  @ParameterizedTest(name = "Deve bloquear CONTABIL para {0}")
  @MethodSource("authenticatedExceptContabilMethods")
  void deveBloquearContabilNasPaginasRestritas(String nomePagina, String methodName)
      throws Exception {
    Method method = PaginaUsuarioController.class.getMethod(methodName);
    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

    assertNotNull(preAuthorize);
    assertEquals(AUTHENTICATED_EXCEPT_CONTABIL_EXPRESSION, preAuthorize.value());
  }

  @ParameterizedTest(name = "Deve exigir role admin para {0}")
  @MethodSource("adminOnlyMethods")
  void deveExigirRoleAdminNasPaginas(String nomePagina, String methodName) throws Exception {
    Method method = PaginaUsuarioController.class.getMethod(methodName);
    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

    assertNotNull(preAuthorize);
    assertEquals(ADMIN_EXPRESSION, preAuthorize.value());
  }

  @ParameterizedTest(name = "Deve exigir role admin ou contabil para {0}")
  @MethodSource("notificationMethods")
  void deveExigirRoleAdminOuContabilNasPaginas(String nomePagina, String methodName)
      throws Exception {
    Method method = PaginaUsuarioController.class.getMethod(methodName);
    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

    assertNotNull(preAuthorize);
    assertEquals(NOTIFICATION_EXPRESSION, preAuthorize.value());
  }

  private static Stream<Arguments> resourcePageMethods() {
    return Stream.of(
        Arguments.of("login", "loginPage", HttpStatus.OK),
        Arguments.of("404", "notFoundPage", HttpStatus.NOT_FOUND));
  }

  private static Stream<Arguments> templatePageMethods() {
    return Stream.of(
        Arguments.of("criar usuario", "criarUsuarioPage", "criar_usuario"),
        Arguments.of("atualizar usuario", "atualizarUsuarioPage", "atualizar_usuario"),
        Arguments.of("adicionar comprovante", "adicionarComprovantePage", "adicionar_comprovante"),
        Arguments.of("home", "homePage", "home"),
        Arguments.of("lista comprovantes", "listaComprovantesPage", "lista_comprovantes"),
        Arguments.of("relatorios", "relatoriosPage", "relatorios"),
        Arguments.of("notificacoes", "notificacoesPage", "notificacoes"),
        Arguments.of("admin", "adminPage", "admin"),
        Arguments.of("gerenciar roles", "gerenciarRolesPage", "gerenciar_roles"));
  }

  private static Stream<Arguments> authenticatedMethods() {
    return Stream.of(
        Arguments.of("home", "homePage"),
        Arguments.of("lista comprovantes", "listaComprovantesPage"),
        Arguments.of("relatorios", "relatoriosPage"));
  }

  private static Stream<Arguments> authenticatedExceptContabilMethods() {
    return Stream.of(Arguments.of("adicionar comprovante", "adicionarComprovantePage"));
  }

  private static Stream<Arguments> adminOnlyMethods() {
    return Stream.of(
        Arguments.of("criar usuario", "criarUsuarioPage"),
        Arguments.of("atualizar usuario", "atualizarUsuarioPage"),
        Arguments.of("admin", "adminPage"),
        Arguments.of("gerenciar roles", "gerenciarRolesPage"));
  }

  private static Stream<Arguments> notificationMethods() {
    return Stream.of(Arguments.of("notificacoes", "notificacoesPage"));
  }

  @SuppressWarnings("unchecked")
  private ResponseEntity<Resource> invokePageMethod(
      PaginaUsuarioController controller, String methodName) throws Exception {
    Method method = PaginaUsuarioController.class.getMethod(methodName);
    return (ResponseEntity<Resource>) method.invoke(controller);
  }

  private String invokeTemplatePageMethod(PaginaUsuarioController controller, String methodName)
      throws Exception {
    Method method = PaginaUsuarioController.class.getMethod(methodName);
    return (String) method.invoke(controller);
  }
}
