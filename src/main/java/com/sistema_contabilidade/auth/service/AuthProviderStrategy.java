package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.config.AuthProvider;
import com.sistema_contabilidade.auth.dto.CompleteNewPasswordRequest;
import com.sistema_contabilidade.auth.dto.LoginRequest;
import com.sistema_contabilidade.auth.model.SessaoUsuario;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public interface AuthProviderStrategy {

  AuthProviderLoginResult login(LoginRequest request);

  default AuthProviderLoginResult completeNewPassword(
      AuthLoginChallenge challenge, CompleteNewPasswordRequest request) {
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "Provider nao suporta troca inicial de senha");
  }

  AuthProviderRefreshResult refresh(SessaoUsuario sessaoUsuario);

  void logout(SessaoUsuario sessaoUsuario);

  boolean supports(AuthProvider provider);
}
