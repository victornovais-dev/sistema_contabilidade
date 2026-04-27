package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.dto.AuthenticatedLoginResult;
import com.sistema_contabilidade.auth.dto.JwtLoginResponse;
import com.sistema_contabilidade.auth.dto.LoginRequest;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import com.sistema_contabilidade.security.service.RequestFingerprintService;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

  private final JwtService jwtService;
  private final UsuarioRepository usuarioRepository;
  private final PasswordEncoder passwordEncoder;
  private final CustomUserDetailsService customUserDetailsService;
  private final SessaoUsuarioService sessaoUsuarioService;
  private final RequestFingerprintService requestFingerprintService;

  private static final String TOKEN_TYPE = "Bearer";
  private static final String CREDENCIAIS_INVALIDAS = "Credenciais invalidas";
  private static final String DUMMY_PASSWORD = "dummy-password-for-timing-protection";

  @Value("${app.auth.login-diagnostics.enabled:false}")
  private boolean loginDiagnosticsEnabled;

  private volatile String cachedDummyPasswordHash;

  public AuthenticatedLoginResult login(LoginRequest request, HttpServletRequest httpRequest) {
    long t0 = System.currentTimeMillis();
    Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(request.email());
    long t1 = System.currentTimeMillis();

    String hashParaComparar = usuarioOpt.map(Usuario::getSenha).orElseGet(this::dummyPasswordHash);
    boolean senhaValida = passwordEncoder.matches(request.senha(), hashParaComparar);
    long t2 = System.currentTimeMillis();

    if (usuarioOpt.isEmpty() || !senhaValida) {
      logDiagnostico(t0, t1, t2, t2);
      throw credenciaisInvalidas();
    }

    Usuario usuario = usuarioOpt.orElseThrow(this::credenciaisInvalidas);
    atualizarSenhaSeNecessario(usuario, request.senha());
    sessaoUsuarioService.revogarSessoesAtivas(usuario.getId());
    String sessionToken = sessaoUsuarioService.criarSessao(usuario.getId());

    UserDetails userDetails = criarUserDetails(usuario);
    String token =
        jwtService.generateToken(
            userDetails, requestFingerprintService.generateFingerprint(httpRequest));
    long t3 = System.currentTimeMillis();
    logDiagnostico(t0, t1, t2, t3);
    return new AuthenticatedLoginResult(new JwtLoginResponse(token, TOKEN_TYPE), sessionToken);
  }

  public JwtLoginResponse refresh(String sessionToken, HttpServletRequest httpRequest) {
    Usuario usuario = usuarioAutenticadoDaSessao(sessionToken);
    String token =
        jwtService.generateToken(
            criarUserDetails(usuario), requestFingerprintService.generateFingerprint(httpRequest));
    return new JwtLoginResponse(token, TOKEN_TYPE);
  }

  public void logout(String sessionToken) {
    if (sessionToken == null || sessionToken.isBlank()) {
      return;
    }
    try {
      sessaoUsuarioService.revogarSessao(sessionToken);
    } catch (ResponseStatusException exception) {
      if (exception.getStatusCode().value() != HttpStatus.UNAUTHORIZED.value()) {
        throw exception;
      }
    }
  }

  private ResponseStatusException credenciaisInvalidas() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, CREDENCIAIS_INVALIDAS);
  }

  private Usuario usuarioAutenticadoDaSessao(String sessionToken) {
    return usuarioRepository
        .findById(sessaoUsuarioService.validarSessao(sessionToken))
        .orElseThrow(this::credenciaisInvalidas);
  }

  private UserDetails criarUserDetails(Usuario usuario) {
    return User.withUsername(usuario.getEmail())
        .password(usuario.getSenha())
        .authorities("ROLE_AUTHENTICATED")
        .build();
  }

  private String dummyPasswordHash() {
    String hash = cachedDummyPasswordHash;
    if (hash != null) {
      return hash;
    }
    synchronized (this) {
      if (cachedDummyPasswordHash == null) {
        cachedDummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD);
      }
      return cachedDummyPasswordHash;
    }
  }

  private void atualizarSenhaSeNecessario(Usuario usuario, String senhaEmTextoPlano) {
    if (!passwordEncoder.upgradeEncoding(usuario.getSenha())) {
      return;
    }

    usuario.setSenha(passwordEncoder.encode(senhaEmTextoPlano));
    Usuario usuarioAtualizado = usuarioRepository.save(usuario);
    customUserDetailsService.atualizarCacheUsuario(
        usuarioAtualizado.getId(), usuarioAtualizado.getEmail());
  }

  private void logDiagnostico(long t0, long t1, long t2, long t3) {
    if (loginDiagnosticsEnabled && log.isInfoEnabled()) {
      log.info(
          "DB={}ms | Argon2={}ms | JWT/Sessao={}ms | Total={}ms",
          (t1 - t0),
          (t2 - t1),
          (t3 - t2),
          (t3 - t0));
    }
  }
}
