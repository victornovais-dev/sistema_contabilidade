package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.config.AuthProvider;
import com.sistema_contabilidade.auth.config.AuthProviderProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthProviderStrategyResolver {

  private final List<AuthProviderStrategy> strategies;
  private final AuthProviderProperties authProviderProperties;

  public AuthProviderStrategy current() {
    return resolve(authProviderProperties.getProvider());
  }

  public AuthProviderStrategy resolve(AuthProvider provider) {
    return strategies.stream()
        .filter(strategy -> strategy.supports(provider))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Nenhuma estrategia de auth encontrada para " + provider));
  }

  public AuthProvider currentProvider() {
    return authProviderProperties.getProvider();
  }
}
