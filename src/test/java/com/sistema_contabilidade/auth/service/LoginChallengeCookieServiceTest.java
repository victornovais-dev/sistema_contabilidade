package com.sistema_contabilidade.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.auth.config.AuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginChallengeCookieService unit tests")
class LoginChallengeCookieServiceTest {

  @Mock private SessionCipherService sessionCipherService;

  private LoginChallengeCookieService loginChallengeCookieService;

  @BeforeEach
  void setUp() {
    loginChallengeCookieService =
        new LoginChallengeCookieService(sessionCipherService, new ObjectMapper());
  }

  @Test
  @DisplayName("Deve criar token cifrado para challenge valido")
  void deveCriarTokenCifradoParaChallengeValido() {
    AuthLoginChallenge challenge =
        new AuthLoginChallenge(
            AuthProvider.COGNITO,
            "NEW_PASSWORD_REQUIRED",
            "ana@email.com",
            "challenge-session",
            "Mensagem");
    when(sessionCipherService.encryptString(org.mockito.ArgumentMatchers.contains("ana@email.com")))
        .thenReturn("token-cifrado");

    String token = loginChallengeCookieService.createToken(challenge);

    assertEquals("token-cifrado", token);
  }

  @Test
  @DisplayName("Deve ler challenge valido a partir do token")
  void deveLerChallengeValidoAPartirDoToken() {
    String rawPayload =
        "{\"provider\":\"COGNITO\",\"challengeName\":\"NEW_PASSWORD_REQUIRED\","
            + "\"providerUsername\":\"ana@email.com\",\"challengeSession\":\"abc\","
            + "\"message\":\"Mensagem\"}";
    when(sessionCipherService.decryptString("token-cifrado")).thenReturn(rawPayload);

    AuthLoginChallenge challenge = loginChallengeCookieService.parseToken("token-cifrado");

    assertEquals("ana@email.com", challenge.providerUsername());
    assertEquals("abc", challenge.challengeSession());
  }

  @Test
  @DisplayName("Deve rejeitar challenge com campos obrigatorios ausentes")
  void deveRejeitarChallengeComCamposObrigatoriosAusentes() {
    String rawPayload =
        "{\"provider\":\"COGNITO\",\"challengeName\":\" \","
            + "\"providerUsername\":\"ana@email.com\",\"challengeSession\":\"abc\","
            + "\"message\":\"Mensagem\"}";
    when(sessionCipherService.decryptString("token-invalido")).thenReturn(rawPayload);

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> loginChallengeCookieService.parseToken("token-invalido"));

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
  }
}
