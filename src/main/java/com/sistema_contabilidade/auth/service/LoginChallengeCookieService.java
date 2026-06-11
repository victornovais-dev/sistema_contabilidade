package com.sistema_contabilidade.auth.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class LoginChallengeCookieService {

  private final SessionCipherService sessionCipherService;
  private final ObjectMapper objectMapper;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "ObjectMapper e configurado pelo Spring e usado apenas para serializar payload interno.")
  public LoginChallengeCookieService(
      SessionCipherService sessionCipherService, ObjectMapper objectMapper) {
    this.sessionCipherService = sessionCipherService;
    this.objectMapper = objectMapper;
  }

  public String createToken(AuthLoginChallenge challenge) {
    try {
      return sessionCipherService.encryptString(objectMapper.writeValueAsString(challenge));
    } catch (Exception exception) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao criar challenge de login", exception);
    }
  }

  public AuthLoginChallenge parseToken(String token) {
    try {
      String rawPayload = sessionCipherService.decryptString(token);
      AuthLoginChallenge challenge = objectMapper.readValue(rawPayload, AuthLoginChallenge.class);
      if (challenge.providerUsername() == null
          || challenge.providerUsername().isBlank()
          || challenge.challengeSession() == null
          || challenge.challengeSession().isBlank()
          || challenge.challengeName() == null
          || challenge.challengeName().isBlank()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Challenge de login invalido");
      }
      return challenge;
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Challenge de login invalido", exception);
    }
  }
}
