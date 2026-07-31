package com.sistema_contabilidade.database.crypto;

import com.sistema_contabilidade.database.crypto.service.BlindIndexService;
import com.sistema_contabilidade.database.crypto.service.DatabaseCryptoService;
import java.util.concurrent.atomic.AtomicReference;

public final class DatabaseCryptoRegistry {

  private static final AtomicReference<DatabaseCryptoService> REGISTERED_CRYPTO_SERVICE =
      new AtomicReference<>();
  private static final AtomicReference<BlindIndexService> REGISTERED_BLIND_INDEX_SERVICE =
      new AtomicReference<>();

  private DatabaseCryptoRegistry() {}

  public static void register(DatabaseCryptoService service) {
    REGISTERED_CRYPTO_SERVICE.set(service);
  }

  public static void register(BlindIndexService service) {
    REGISTERED_BLIND_INDEX_SERVICE.set(service);
  }

  static DatabaseCryptoService cryptoService() {
    DatabaseCryptoService service = REGISTERED_CRYPTO_SERVICE.get();
    if (service == null) {
      throw new DatabaseCryptoException("Servico de criptografia do banco ainda nao inicializado");
    }
    return service;
  }

  static BlindIndexService blindIndexService() {
    BlindIndexService service = REGISTERED_BLIND_INDEX_SERVICE.get();
    if (service == null) {
      throw new DatabaseCryptoException("Servico de blind index ainda nao inicializado");
    }
    return service;
  }
}
