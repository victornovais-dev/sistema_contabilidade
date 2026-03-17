package com.sistema_contabilidade.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.auth.dto.JwtLoginResponse;
import com.sistema_contabilidade.auth.dto.LoginRequest;
import com.sistema_contabilidade.security.service.JwtService;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService unit tests")
class AuthServiceTest {

  @Mock private AuthenticationManager authenticationManager;
  @Mock private JwtService jwtService;
  @Mock private UsuarioRepository usuarioRepository;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private AuthService authService;

  @Test
  @DisplayName("Deve autenticar e retornar token JWT")
  void loginDeveRetornarJwtLoginResponse() {
    // Arrange
    LoginRequest request = new LoginRequest("ana@email.com", "123456");
    UserDetails userDetails = new User("ana@email.com", "123456", Collections.emptyList());
    when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new UsernamePasswordAuthenticationToken(
                userDetails, userDetails.getPassword(), userDetails.getAuthorities()));
    when(jwtService.generateToken(userDetails)).thenReturn("jwt-token");

    // Act
    JwtLoginResponse response = authService.login(request);

    // Assert
    assertEquals("jwt-token", response.accessToken());
    assertEquals("Bearer", response.tokenType());
    verify(authenticationManager).authenticate(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("Deve usar login com diagnostico quando habilitado")
  void loginComDiagnosticoDeveRetornarJwtLoginResponse() {
    ReflectionTestUtils.setField(authService, "loginDiagnosticsEnabled", true);
    LoginRequest request = new LoginRequest("ana@email.com", "123456");

    Usuario usuario = new Usuario();
    usuario.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    usuario.setEmail("ana@email.com");
    usuario.setSenha("hash");

    when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(usuario));
    when(passwordEncoder.matches("123456", "hash")).thenReturn(true);
    when(jwtService.generateToken(org.mockito.ArgumentMatchers.any())).thenReturn("jwt-token");

    JwtLoginResponse response = authService.login(request);

    assertEquals("jwt-token", response.accessToken());
    assertEquals("Bearer", response.tokenType());
    verify(authenticationManager, never()).authenticate(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("Deve retornar 401 no diagnostico quando senha for invalida")
  void loginComDiagnosticoSenhaInvalidaDeveRetornarUnauthorized() {
    ReflectionTestUtils.setField(authService, "loginDiagnosticsEnabled", true);
    LoginRequest request = new LoginRequest("ana@email.com", "senha-incorreta");

    Usuario usuario = new Usuario();
    usuario.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    usuario.setEmail("ana@email.com");
    usuario.setSenha("hash");

    when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(usuario));
    when(passwordEncoder.matches("senha-incorreta", "hash")).thenReturn(false);

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> authService.login(request));

    assertEquals(401, exception.getStatusCode().value());
    verify(jwtService, never()).generateToken(org.mockito.ArgumentMatchers.any());
    verify(authenticationManager, never()).authenticate(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("Deve retornar 401 quando principal nao for UserDetails")
  void loginComPrincipalInvalidoDeveRetornarUnauthorized() {
    LoginRequest request = new LoginRequest("ana@email.com", "123456");
    when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new UsernamePasswordAuthenticationToken("principal", "credencial"));

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> authService.login(request));

    assertEquals(401, exception.getStatusCode().value());
    assertEquals(AuthenticationCredentialsNotFoundException.class, exception.getCause().getClass());
  }
}
