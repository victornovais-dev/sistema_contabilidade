package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.dto.JwtLoginResponse;
import com.sistema_contabilidade.auth.dto.LoginRequest;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UsuarioRepository usuarioRepository;
  private final AuthenticationManager authenticationManager;
  private final CustomUserDetailsService userDetailsService;
  private final JwtService jwtService;

  public JwtLoginResponse login(LoginRequest request) {
    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(request.email(), request.senha()));

    Usuario usuario =
        usuarioRepository
            .findByEmail(request.email())
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais invalidas"));

    UserDetails userDetails = userDetailsService.loadUserByUsername(usuario.getEmail());
    String token = jwtService.generateToken(userDetails);
    return new JwtLoginResponse(token, "Bearer");
  }
}
