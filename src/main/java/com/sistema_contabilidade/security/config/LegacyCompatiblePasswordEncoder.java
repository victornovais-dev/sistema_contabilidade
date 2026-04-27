package com.sistema_contabilidade.security.config;

import org.springframework.security.crypto.password.PasswordEncoder;

final class LegacyCompatiblePasswordEncoder implements PasswordEncoder {

  private static final String ARGON2_PREFIX = "{argon2}";

  private final PasswordEncoder delegate;
  private final PasswordEncoder preferredEncoder;
  private final PasswordEncoder legacyMatcher;

  LegacyCompatiblePasswordEncoder(
      PasswordEncoder delegate, PasswordEncoder preferredEncoder, PasswordEncoder legacyMatcher) {
    this.delegate = delegate;
    this.preferredEncoder = preferredEncoder;
    this.legacyMatcher = legacyMatcher;
  }

  @Override
  public String encode(CharSequence rawPassword) {
    return ARGON2_PREFIX + preferredEncoder.encode(rawPassword);
  }

  @Override
  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    if (encodedPassword == null || encodedPassword.isBlank()) {
      return false;
    }

    if (hasEncodingPrefix(encodedPassword)) {
      return delegate.matches(rawPassword, encodedPassword);
    }

    return legacyMatcher.matches(rawPassword, encodedPassword);
  }

  @Override
  public boolean upgradeEncoding(String encodedPassword) {
    if (encodedPassword == null || encodedPassword.isBlank()) {
      return false;
    }

    if (!hasEncodingPrefix(encodedPassword)) {
      return true;
    }

    if (encodedPassword.startsWith(ARGON2_PREFIX)) {
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
