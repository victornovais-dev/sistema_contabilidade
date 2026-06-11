package com.sistema_contabilidade.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class AuthFailureSupport {

  private AuthFailureSupport() {}

  public static ResponseStatusException invalidCredentials() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais invalidas");
  }

  public static ResponseStatusException invalidCredentials(Throwable cause) {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais invalidas", cause);
  }

  public static ResponseStatusException externalAuthFailure(String reason) {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, reason);
  }
}
