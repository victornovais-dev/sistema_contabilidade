package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.config.AuthProvider;
import com.sistema_contabilidade.auth.dto.CompleteNewPasswordRequest;
import com.sistema_contabilidade.auth.dto.LoginRequest;
import com.sistema_contabilidade.auth.model.SessaoUsuario;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

    if (usuario.isTrocaSenhaObrigatoria()) {
      return new AuthProviderLoginResult(
          AuthProvider.LOCAL,
          null,
          usuario.getEmail(),
          usuario.getEmail(),
          null,
          null,
          Set.of(),
          null,
          new AuthLoginChallenge(
              AuthProvider.LOCAL,
              "NEW_PASSWORD_REQUIRED",
              usuario.getEmail(),
              null,
              "Troca de senha obrigatoria. Defina uma nova senha para continuar."));
    }

    return authenticatedResult(usuario);
  }

  @Override
  public AuthProviderLoginResult completeNewPassword(
      AuthLoginChallenge challenge, CompleteNewPasswordRequest request) {
    if (!"NEW_PASSWORD_REQUIRED".equals(challenge.challengeName())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Challenge de login invalido");
    }
    Usuario usuario =
        usuarioRepository
            .findByEmail(challenge.providerUsername())
            .orElseThrow(AuthFailureSupport::invalidCredentials);
    if (!usuario.isTrocaSenhaObrigatoria()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Troca de senha nao esta pendente");
    }
    usuario.setSenha(passwordEncoder.encode(request.novaSenha()));
    usuario.setTrocaSenhaObrigatoria(false);
    return authenticatedResult(usuarioRepository.save(usuario));
  }

  private AuthProviderLoginResult authenticatedResult(Usuario usuario) {
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
