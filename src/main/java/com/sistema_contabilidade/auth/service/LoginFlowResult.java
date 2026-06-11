package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.dto.AuthenticatedLoginResult;

public record LoginFlowResult(
    AuthenticatedLoginResult authenticatedResult, AuthLoginChallenge challenge) {

  public static LoginFlowResult authenticated(AuthenticatedLoginResult authenticatedResult) {
    return new LoginFlowResult(authenticatedResult, null);
  }

  public static LoginFlowResult challenge(AuthLoginChallenge challenge) {
    return new LoginFlowResult(null, challenge);
  }

  public boolean challengeRequired() {
    return challenge != null;
  }
}
