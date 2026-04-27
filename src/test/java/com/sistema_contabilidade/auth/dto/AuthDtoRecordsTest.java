package com.sistema_contabilidade.auth.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Auth DTO records unit tests")
class AuthDtoRecordsTest {

  @Test
  @DisplayName("Deve expor campos de LoginResponse")
  void deveExporCamposDeLoginResponse() {
    UUID usuarioId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    LoginResponse response = new LoginResponse("ok", usuarioId);

    assertEquals("ok", response.message());
    assertEquals(usuarioId, response.usuarioId());
  }

  @Test
  @DisplayName("Deve expor campos de AuthenticatedLoginResult")
  void deveExporCamposDeAuthenticatedLoginResult() {
    AuthenticatedLoginResult result =
        new AuthenticatedLoginResult(new JwtLoginResponse("jwt", "Bearer"), "sessao");

    assertEquals("jwt", result.response().accessToken());
    assertEquals("sessao", result.sessionToken());
  }

  @Test
  @DisplayName("Deve expor campos de LoginSessionResult")
  void deveExporCamposDeLoginSessionResult() {
    UUID usuarioId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    LoginSessionResult result = new LoginSessionResult(usuarioId, "token");

    assertEquals(usuarioId, result.usuarioId());
    assertEquals("token", result.token());
  }

  @Test
  @DisplayName("Deve expor campos de SessionValidationResponse")
  void deveExporCamposDeSessionValidationResponse() {
    UUID usuarioId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    SessionValidationResponse response = new SessionValidationResponse(false, usuarioId);

    assertFalse(response.valid());
    assertEquals(usuarioId, response.usuarioId());
  }
}
