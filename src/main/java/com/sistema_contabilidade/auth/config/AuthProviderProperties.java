package com.sistema_contabilidade.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AuthProviderProperties {

  private AuthProvider provider = AuthProvider.LOCAL;
  private boolean allowLocalInProd;

  public AuthProvider getProvider() {
    return provider;
  }

  public void setProvider(AuthProvider provider) {
    this.provider = provider == null ? AuthProvider.LOCAL : provider;
  }

  public boolean isAllowLocalInProd() {
    return allowLocalInProd;
  }

  public void setAllowLocalInProd(boolean allowLocalInProd) {
    this.allowLocalInProd = allowLocalInProd;
  }
}
