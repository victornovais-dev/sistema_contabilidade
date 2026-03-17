package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.dto.JwtLoginResponse;
import com.sistema_contabilidade.auth.dto.LoginRequest;
import com.sistema_contabilidade.security.service.JwtService;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

  private final AuthenticationManager authenticationManager;
  private final JwtService jwtService;
  private final UsuarioRepository usuarioRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${app.auth.login-diagnostics.enabled:false}")
  private boolean loginDiagnosticsEnabled;

  public JwtLoginResponse login(LoginRequest request) {
    if (loginDiagnosticsEnabled) {
      return loginComDiagnostico(request);
    }

    Authentication authentication =
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.senha()));
    UserDetails userDetails = extrairUserDetails(authentication);
    String token = jwtService.generateToken(userDetails);
    return new JwtLoginResponse(token, "Bearer");
  }

  private JwtLoginResponse loginComDiagnostico(LoginRequest request) {
    long t0 = System.currentTimeMillis();

    Usuario usuario =
        usuarioRepository.findByEmail(request.email()).orElseThrow(this::credenciaisInvalidas);
    long t1 = System.currentTimeMillis();

    boolean senhaValida = passwordEncoder.matches(request.senha(), usuario.getSenha());
    long t2 = System.currentTimeMillis();

    if (!senhaValida) {
      if (log.isInfoEnabled()) {
        log.info("DB={}ms | Scrypt={}ms | JWT=0ms | Total={}ms", (t1 - t0), (t2 - t1), (t2 - t0));
      }
      throw credenciaisInvalidas();
    }

    UserDetails userDetails =
        org.springframework.security.core.userdetails.User.withUsername(usuario.getEmail())
            .password(usuario.getSenha())
            .authorities("ROLE_AUTHENTICATED")
            .build();
    String token = jwtService.generateToken(userDetails);
    long t3 = System.currentTimeMillis();

    if (log.isInfoEnabled()) {
      log.info(
          "DB={}ms | Scrypt={}ms | JWT={}ms | Total={}ms",
          (t1 - t0),
          (t2 - t1),
          (t3 - t2),
          (t3 - t0));
    }

    return new JwtLoginResponse(token, "Bearer");
  }

  private ResponseStatusException credenciaisInvalidas() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais invalidas");
  }

  private UserDetails extrairUserDetails(Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (principal instanceof UserDetails userDetails) {
      return userDetails;
    }
    throw new ResponseStatusException(
        HttpStatus.UNAUTHORIZED,
        "Credenciais invalidas",
        new AuthenticationCredentialsNotFoundException("Principal sem UserDetails"));
  }
}
