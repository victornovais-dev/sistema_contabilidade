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
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("UsuarioExceptionHandler unit tests")
class UsuarioExceptionHandlerTest {

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
  @DisplayName("Deve mapear erro de validação")
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
  @DisplayName("Deve mapear exceção inesperada para 500")
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
    void dummy(UsuarioCreateRequest request) {}
  }
}
