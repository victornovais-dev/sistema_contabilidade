package com.sistema_contabilidade.auth.dto;

public record LoginApiResponse(
    String accessToken,
    String tokenType,
    boolean challengeRequired,
    String challengeName,
    String message) {

  public static LoginApiResponse authenticated(JwtLoginResponse response) {
    return new LoginApiResponse(response.accessToken(), response.tokenType(), false, null, null);
  }

  public static LoginApiResponse challenge(String challengeName, String message) {
    return new LoginApiResponse(null, null, true, challengeName, message);
  }
}
