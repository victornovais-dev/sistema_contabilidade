package com.sistema_contabilidade.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.auth.dto.JwtLoginResponse;
import com.sistema_contabilidade.auth.dto.LoginRequest;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService unit tests")
class AuthServiceTest {

  @Mock private UsuarioRepository usuarioRepository;
  @Mock private AuthenticationManager authenticationManager;
  @Mock private CustomUserDetailsService userDetailsService;
  @Mock private JwtService jwtService;

  @InjectMocks private AuthService authService;

  @Test
  @DisplayName("Deve autenticar e retornar token JWT")
  void loginDeveRetornarJwtLoginResponse() {
    // Arrange
    LoginRequest request = new LoginRequest("ana@email.com", "123456");
    Usuario usuario = new Usuario();
    usuario.setEmail("ana@email.com");
    UserDetails userDetails = new User("ana@email.com", "123456", Collections.emptyList());
    when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(usuario));
    when(userDetailsService.loadUserByUsername("ana@email.com")).thenReturn(userDetails);
    when(jwtService.generateToken(userDetails)).thenReturn("jwt-token");

    // Act
    JwtLoginResponse response = authService.login(request);

    // Assert
    assertEquals("jwt-token", response.accessToken());
    assertEquals("Bearer", response.tokenType());
    verify(authenticationManager).authenticate(org.mockito.ArgumentMatchers.any());
  }
}
