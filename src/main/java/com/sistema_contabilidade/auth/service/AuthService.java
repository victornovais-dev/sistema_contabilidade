package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.dto.JwtLoginResponse;
import com.sistema_contabilidade.auth.dto.LoginRequest;
import com.sistema_contabilidade.security.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final AuthenticationManager authenticationManager;
  private final JwtService jwtService;

  public JwtLoginResponse login(LoginRequest request) {
    Authentication authentication =
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.senha()));
    UserDetails userDetails = extrairUserDetails(authentication);
    String token = jwtService.generateToken(userDetails);
    return new JwtLoginResponse(token, "Bearer");
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
