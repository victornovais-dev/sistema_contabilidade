package com.sistema_contabilidade.database.crypto.service;

import com.sistema_contabilidade.database.crypto.DatabaseCryptoRegistry;
import jakarta.annotation.PostConstruct;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class BlindIndexService {

  private static final String USER_EMAIL_CONTEXT = "usuario.email";
  private static final String USER_COGNITO_SUB_CONTEXT = "usuario.cognito-sub";
  private static final String USER_COGNITO_USERNAME_CONTEXT = "usuario.cognito-username";
  private static final String ITEM_DOCUMENT_CONTEXT = "item.cnpj-cpf";

  private final DatabaseCryptoService cryptoService;

  public BlindIndexService(DatabaseCryptoService cryptoService) {
    this.cryptoService = cryptoService;
  }

  @PostConstruct
  void register() {
    DatabaseCryptoRegistry.register(this);
  }

  public String email(String value) {
    return cryptoService.blindIndex(USER_EMAIL_CONTEXT, normalizeCaseInsensitive(value));
  }

  public String cognitoSub(String value) {
    return cryptoService.blindIndex(USER_COGNITO_SUB_CONTEXT, normalizeOpaque(value));
  }

  public String cognitoUsername(String value) {
    return cryptoService.blindIndex(USER_COGNITO_USERNAME_CONTEXT, normalizeCaseInsensitive(value));
  }

  public String document(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.replaceAll("[^0-9A-Za-z]", "").toUpperCase(Locale.ROOT);
    return cryptoService.blindIndex(ITEM_DOCUMENT_CONTEXT, normalized);
  }

  private String normalizeCaseInsensitive(String value) {
    return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
  }

  private String normalizeOpaque(String value) {
    return value == null ? null : value.trim();
  }
}
