package com.sistema_contabilidade.usuario.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

@RestControllerAdvice
@Slf4j
public class UsuarioExceptionHandler {

  private static boolean acceptsHtml(HttpServletRequest request) {
    if (request == null) {
      return false;
    }
    String accept = request.getHeader("Accept");
    return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleAuthenticationException(
      AuthenticationException ex, WebRequest request) {
    ErrorResponse error =
        new ErrorResponse(
            HttpStatus.UNAUTHORIZED.value(),
            ex.getMessage() == null ? "Falha na autenticacao" : ex.getMessage(),
            request.getDescription(false),
            LocalDateTime.now());
    return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ErrorResponse> handleResponseStatusException(
      ResponseStatusException ex, WebRequest request) {
    HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
    ErrorResponse error =
        new ErrorResponse(
            status.value(),
            ex.getReason() == null ? "Erro na requisicao" : ex.getReason(),
            request.getDescription(false),
            LocalDateTime.now());
    return new ResponseEntity<>(error, status);
  }

  @ExceptionHandler({
    ObjectOptimisticLockingFailureException.class,
    OptimisticLockingFailureException.class
  })
  public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
      RuntimeException ex, WebRequest request) {
    ErrorResponse error =
        new ErrorResponse(
            HttpStatus.CONFLICT.value(),
            "Registro alterado por outra operacao concorrente. Atualize os dados e tente novamente.",
            request.getDescription(false),
            LocalDateTime.now());
    return new ResponseEntity<>(error, HttpStatus.CONFLICT);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ValidationErrorResponse> handleValidation(
      MethodArgumentNotValidException ex) {
    Map<String, String> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    FieldError::getField,
                    error ->
                        error.getDefaultMessage() == null
                            ? "Valor invalido"
                            : error.getDefaultMessage(),
                    (current, ignored) -> current));

    ValidationErrorResponse response =
        new ValidationErrorResponse(
            HttpStatus.BAD_REQUEST.value(), "Validation failed", errors, LocalDateTime.now());
    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex, WebRequest request) {
    Throwable cause = ex.getMostSpecificCause();
    String message = "Payload invalido";

    if (cause instanceof UnrecognizedPropertyException unknownPropertyException) {
      message = "Campo nao permitido: " + unknownPropertyException.getPropertyName();
    } else if (cause instanceof StreamReadException streamReadException
        && streamReadException.getMessage() != null
        && streamReadException.getMessage().contains("Duplicate Object property")) {
      message = "JSON invalido: campo duplicado";
    }

    ErrorResponse error =
        new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            message,
            request.getDescription(false),
            LocalDateTime.now());
    return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Object> handleNoResourceFoundException(
      NoResourceFoundException ex, WebRequest webRequest, HttpServletRequest httpRequest) {
    if (log.isDebugEnabled()) {
      log.debug("Recurso estatico nao encontrado: {}", ex.getResourcePath());
    }

    if (acceptsHtml(httpRequest) && !httpRequest.getRequestURI().startsWith("/api/")) {
      Resource resource = new ClassPathResource("static/error/404.html");
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.TEXT_HTML)
          .body(resource);
    }

    ErrorResponse error =
        new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            "Recurso nao encontrado",
            webRequest.getDescription(false),
            LocalDateTime.now());
    return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex, WebRequest request) {
    log.error("Erro inesperado", ex);
    ErrorResponse error =
        new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "An unexpected error occurred",
            request.getDescription(false),
            LocalDateTime.now());
    return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
  }
}

record ErrorResponse(int status, String message, String path, LocalDateTime timestamp) {}

record ValidationErrorResponse(
    int status, String message, Map<String, String> errors, LocalDateTime timestamp) {}
