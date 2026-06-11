package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.config.AuthProvider;
import com.sistema_contabilidade.auth.dto.LoginRequest;
import com.sistema_contabilidade.auth.model.SessaoUsuario;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocalAuthProviderStrategy implements AuthProviderStrategy {

  private final UsuarioRepository usuarioRepository;
  private final PasswordEncoder passwordEncoder;
  private volatile String cachedTimingProtectionHash;

  @Override
  public AuthProviderLoginResult login(LoginRequest request) {
    Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(request.email());
    String hashParaComparar =
        usuarioOpt.map(Usuario::getSenha).orElseGet(this::timingProtectionHash);
    boolean senhaValida = passwordEncoder.matches(request.senha(), hashParaComparar);
    if (usuarioOpt.isEmpty() || !senhaValida) {
      throw AuthFailureSupport.invalidCredentials();
    }

    Usuario usuario = usuarioOpt.orElseThrow(AuthFailureSupport::invalidCredentials);
    if (passwordEncoder.upgradeEncoding(usuario.getSenha())) {
      usuario.setSenha(passwordEncoder.encode(request.senha()));
      usuario = usuarioRepository.save(usuario);
    }

    return new AuthProviderLoginResult(
        AuthProvider.LOCAL,
        usuario.getId(),
        usuario.getEmail(),
        usuario.getEmail(),
        usuario.getNome(),
        usuario.getCognitoSub(),
        Set.of(),
        null,
        null);
  }

  @Override
  public AuthProviderRefreshResult refresh(SessaoUsuario sessaoUsuario) {
    return new AuthProviderRefreshResult(
        AuthProvider.LOCAL,
        sessaoUsuario.getAuthUsername(),
        sessaoUsuario.getAuthUsername(),
        null,
        sessaoUsuario.getCognitoSub(),
        Set.of(),
        null);
  }

  @Override
  public void logout(SessaoUsuario sessaoUsuario) {
    // Sem logout externo para provider local.
  }

  @Override
  public boolean supports(AuthProvider provider) {
    return provider == AuthProvider.LOCAL;
  }

  private String timingProtectionHash() {
    String hash = cachedTimingProtectionHash;
    if (hash != null) {
      return hash;
    }
    synchronized (this) {
      if (cachedTimingProtectionHash == null) {
        cachedTimingProtectionHash = passwordEncoder.encode(UUID.randomUUID().toString());
      }
      return cachedTimingProtectionHash;
    }
  }
}
