package com.sistema_contabilidade.security.config;

import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;

final class LegacyCompatiblePasswordEncoder implements PasswordEncoder {

  private static final String SCRYPT_PREFIX = "{scrypt}";

  private final PasswordEncoder delegate;
  private final PasswordEncoder preferredEncoder;
  private final List<PasswordEncoder> legacyMatchers;

  LegacyCompatiblePasswordEncoder(PasswordEncoder delegate, PasswordEncoder preferredEncoder) {
    this.delegate = delegate;
    this.preferredEncoder = preferredEncoder;
    this.legacyMatchers =
        List.of(
            SCryptPasswordEncoder.defaultsForSpringSecurity_v4_1(),
            SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8());
  }

  @Override
  public String encode(CharSequence rawPassword) {
    return SCRYPT_PREFIX + preferredEncoder.encode(rawPassword);
  }

  @Override
  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    if (encodedPassword == null || encodedPassword.isBlank()) {
      return false;
    }

    if (hasEncodingPrefix(encodedPassword)) {
      return delegate.matches(rawPassword, encodedPassword);
    }

    return legacyMatchers.stream()
        .anyMatch(encoder -> encoder.matches(rawPassword, encodedPassword));
  }

  @Override
  public boolean upgradeEncoding(String encodedPassword) {
    if (encodedPassword == null || encodedPassword.isBlank()) {
      return false;
    }

    if (!hasEncodingPrefix(encodedPassword)) {
      return true;
    }

    if (encodedPassword.startsWith(SCRYPT_PREFIX)) {
      return preferredEncoder.upgradeEncoding(removePrefix(encodedPassword));
    }

    return true;
  }

  private boolean hasEncodingPrefix(String encodedPassword) {
    return encodedPassword.startsWith("{") && encodedPassword.indexOf('}') > 1;
  }

  private String removePrefix(String encodedPassword) {
    return encodedPassword.substring(encodedPassword.indexOf('}') + 1);
  }
}
