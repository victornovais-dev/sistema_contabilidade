package com.sistema_contabilidade.database.crypto;

public class DatabaseCryptoException extends IllegalStateException {

  private static final long serialVersionUID = 1L;

  public DatabaseCryptoException(String message) {
    super(message);
  }

  public DatabaseCryptoException(String message, Throwable cause) {
    super(message, cause);
  }
}
