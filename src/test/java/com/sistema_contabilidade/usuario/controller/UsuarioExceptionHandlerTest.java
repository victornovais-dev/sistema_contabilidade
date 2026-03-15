package com.sistema_contabilidade.usuario.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.usuario.dto.UsuarioCreateRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("UsuarioExceptionHandler unit tests")
class UsuarioExceptionHandlerTest {

  @Test
  @DisplayName("Deve mapear AuthenticationException para 401")
  void deveMapearAuthenticationExceptionPara401() {
    UsuarioExceptionHandler handler = new UsuarioExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    BadCredentialsException exception =
        new BadCredentialsException("Usuario inexistente ou senha invalida");

    ResponseEntity<ErrorResponse> response =
        handler.handleAuthenticationException(exception, new ServletWebRequest(request));

    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    assertEquals("Usuario inexistente ou senha invalida", response.getBody().message());
  }

  @Test
  @DisplayName("Deve usar fallback na AuthenticationException sem mensagem")
  void deveUsarFallbackNaAuthenticationExceptionSemMensagem() {
    UsuarioExceptionHandler handler = new UsuarioExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    AuthenticationException exception = new BadCredentialsException(null);

    ResponseEntity<ErrorResponse> response =
        handler.handleAuthenticationException(exception, new ServletWebRequest(request));

    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    assertEquals("Falha na autenticacao", response.getBody().message());
  }

  @Test
  @DisplayName("Deve mapear ResponseStatusException com fallback de mensagem")
  void deveMapearResponseStatusExceptionComFallbackDeMensagem() {
    UsuarioExceptionHandler handler = new UsuarioExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    ResponseStatusException exception = new ResponseStatusException(HttpStatus.BAD_REQUEST);

    ResponseEntity<ErrorResponse> response =
        handler.handleResponseStatusException(exception, new ServletWebRequest(request));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("Erro na requisicao", response.getBody().message());
    assertTrue(response.getBody().path().contains("/api/v1/usuarios"));
  }

  @Test
  @DisplayName("Deve preservar motivo na ResponseStatusException")
  void devePreservarMotivoNaResponseStatusException() {
    UsuarioExceptionHandler handler = new UsuarioExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    ResponseStatusException exception =
        new ResponseStatusException(HttpStatus.CONFLICT, "Email ja cadastrado");

    ResponseEntity<ErrorResponse> response =
        handler.handleResponseStatusException(exception, new ServletWebRequest(request));

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    assertEquals("Email ja cadastrado", response.getBody().message());
  }

  @Test
  @DisplayName("Deve mapear erro de validacao")
  void deveMapearErroDeValidacao() throws Exception {
    UsuarioExceptionHandler handler = new UsuarioExceptionHandler();
    BeanPropertyBindingResult bindingResult =
        new BeanPropertyBindingResult(new Object(), "usuarioCreateRequest");
    bindingResult.addError(
        new FieldError("usuarioCreateRequest", "email", "Email deve ser valido"));
    bindingResult.addError(new FieldError("usuarioCreateRequest", "email", "Outra mensagem"));
    Method method = DummyController.class.getDeclaredMethod("dummy", UsuarioCreateRequest.class);
    MethodParameter methodParameter = new MethodParameter(method, 0);
    MethodArgumentNotValidException exception =
        new MethodArgumentNotValidException(methodParameter, bindingResult);

    ResponseEntity<ValidationErrorResponse> response = handler.handleValidation(exception);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("Validation failed", response.getBody().message());
    assertEquals("Email deve ser valido", response.getBody().errors().get("email"));
  }

  @Test
  @DisplayName("Deve usar mensagem padrao quando erro de validacao nao informar mensagem")
  void deveUsarMensagemPadraoQuandoErroValidacaoSemMensagem() throws Exception {
    UsuarioExceptionHandler handler = new UsuarioExceptionHandler();
    BeanPropertyBindingResult bindingResult =
        new BeanPropertyBindingResult(new Object(), "usuarioCreateRequest");
    bindingResult.addError(new FieldError("usuarioCreateRequest", "nome", null));
    Method method = DummyController.class.getDeclaredMethod("dummy", UsuarioCreateRequest.class);
    MethodParameter methodParameter = new MethodParameter(method, 0);
    MethodArgumentNotValidException exception =
        new MethodArgumentNotValidException(methodParameter, bindingResult);

    ResponseEntity<ValidationErrorResponse> response = handler.handleValidation(exception);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("Valor invalido", response.getBody().errors().get("nome"));
  }

  @Test
  @DisplayName("Deve mapear excecao inesperada para 500")
  void deveMapearExcecaoInesperadaPara500() {
    UsuarioExceptionHandler handler = new UsuarioExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");

    ResponseEntity<ErrorResponse> response =
        handler.handleUnexpectedException(
            new RuntimeException("falha"), new ServletWebRequest(request));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals("An unexpected error occurred", response.getBody().message());
  }

  private static class DummyController {
    @SuppressWarnings("unused")
    void dummy(UsuarioCreateRequest request) {
      throw new UnsupportedOperationException("Nao deve ser chamado em teste");
    }
  }
}
