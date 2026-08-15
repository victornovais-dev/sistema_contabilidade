package com.sistema_contabilidade.auth.dto;

import java.util.UUID;

public record AuthenticatedLoginResult(
    JwtLoginResponse response, String sessionToken, UUID sessionId) {

  public AuthenticatedLoginResult(JwtLoginResponse response, String sessionToken) {
    this(response, sessionToken, null);
  }
}
