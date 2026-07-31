package com.sistema_contabilidade.database.crypto;

import com.sistema_contabilidade.database.crypto.service.DatabaseCryptoService;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

  private DatabaseCryptoService injectedCryptoService;

  public EncryptedStringConverter() {}

  @Autowired
  public EncryptedStringConverter(DatabaseCryptoService cryptoService) {
    this.injectedCryptoService = cryptoService;
  }

  @Override
  public String convertToDatabaseColumn(String attribute) {
    return resolvedCryptoService().encrypt(attribute);
  }

  @Override
  public String convertToEntityAttribute(String databaseValue) {
    return resolvedCryptoService().decrypt(databaseValue);
  }

  private DatabaseCryptoService resolvedCryptoService() {
    return injectedCryptoService == null
        ? DatabaseCryptoRegistry.cryptoService()
        : injectedCryptoService;
  }
}
