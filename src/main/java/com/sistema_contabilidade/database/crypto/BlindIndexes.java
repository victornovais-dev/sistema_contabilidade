package com.sistema_contabilidade.database.crypto;

public final class BlindIndexes {

  private BlindIndexes() {}

  public static String email(String value) {
    return DatabaseCryptoRegistry.blindIndexService().email(value);
  }

  public static String cognitoSub(String value) {
    return DatabaseCryptoRegistry.blindIndexService().cognitoSub(value);
  }

  public static String cognitoUsername(String value) {
    return DatabaseCryptoRegistry.blindIndexService().cognitoUsername(value);
  }

  public static String document(String value) {
    return DatabaseCryptoRegistry.blindIndexService().document(value);
  }
}
