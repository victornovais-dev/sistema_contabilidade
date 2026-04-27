package com.sistema_contabilidade.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.auth.dto.AuthenticatedLoginResult;
import com.sistema_contabilidade.auth.dto.JwtLoginResponse;
import com.sistema_contabilidade.auth.dto.LoginRequest;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import com.sistema_contabilidade.security.service.RequestFingerprintService;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService unit tests")
class AuthServiceTest {

  @Mock private JwtService jwtService;
  @Mock private UsuarioRepository usuarioRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private CustomUserDetailsService customUserDetailsService;
  @Mock private SessaoUsuarioService sessaoUsuarioService;
  @Mock private RequestFingerprintService requestFingerprintService;

  @InjectMocks private AuthService authService;

  @Test
  @DisplayName("Deve autenticar e retornar access token com sessao opaca")
  void loginDeveRetornarAccessTokenESessao() {
    LoginRequest request = new LoginRequest("ana@email.com", "123456");
    MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    Usuario usuario = novoUsuario("ana@email.com", "{argon2}hash-atual");

    when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(usuario));
    when(passwordEncoder.matches("123456", "{argon2}hash-atual")).thenReturn(true);
    when(passwordEncoder.upgradeEncoding("{argon2}hash-atual")).thenReturn(false);
    when(sessaoUsuarioService.criarSessao(usuario.getId())).thenReturn("sessao-segura");
    when(requestFingerprintService.generateFingerprint(httpRequest)).thenReturn("fingerprint");
    when(jwtService.generateToken(any(), any())).thenReturn("jwt-token");

    AuthenticatedLoginResult response = authService.login(request, httpRequest);

    assertEquals("jwt-token", response.response().accessToken());
    assertEquals("Bearer", response.response().tokenType());
    assertEquals("sessao-segura", response.sessionToken());
    verify(sessaoUsuarioService).revogarSessoesAtivas(usuario.getId());
  }

  @Test
  @DisplayName("Deve atualizar hash legado para Argon2id no login com sucesso")
  void loginDeveAtualizarHashQuandoNecessario() {
    LoginRequest request = new LoginRequest("ana@email.com", "123456");
    MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    Usuario usuario = novoUsuario("ana@email.com", "$d0801$hash-legado");

    when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(usuario));
    when(passwordEncoder.matches("123456", "$d0801$hash-legado")).thenReturn(true);
    when(passwordEncoder.upgradeEncoding("$d0801$hash-legado")).thenReturn(true);
    when(passwordEncoder.encode("123456")).thenReturn("{argon2}hash-novo");
    when(usuarioRepository.save(usuario)).thenReturn(usuario);
    when(sessaoUsuarioService.criarSessao(usuario.getId())).thenReturn("sessao-segura");
    when(requestFingerprintService.generateFingerprint(httpRequest)).thenReturn("fingerprint");
    when(jwtService.generateToken(any(), any())).thenReturn("jwt-token");

    AuthenticatedLoginResult response = authService.login(request, httpRequest);

    assertEquals("jwt-token", response.response().accessToken());
    assertEquals("{argon2}hash-novo", usuario.getSenha());
    verify(usuarioRepository).save(usuario);
    verify(customUserDetailsService).atualizarCacheUsuario(usuario.getId(), "ana@email.com");
  }

  @Test
  @DisplayName("Deve retornar erro generico quando usuario nao existe")
  void loginDeveRetornarErroGenericoQuandoUsuarioNaoExiste() {
    LoginRequest request = new LoginRequest("ausente@email.com", "123456");
    MockHttpServletRequest httpRequest = new MockHttpServletRequest();

    when(usuarioRepository.findByEmail("ausente@email.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode(any())).thenReturn("{argon2}dummy-hash");
    when(passwordEncoder.matches("123456", "{argon2}dummy-hash")).thenReturn(false);

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> authService.login(request, httpRequest));

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    assertEquals("Credenciais invalidas", exception.getReason());
    verify(jwtService, never()).generateToken(any(), any());
    verify(sessaoUsuarioService, never()).criarSessao(any());
  }

  @Test
  @DisplayName("Deve retornar erro generico quando senha for invalida")
  void loginDeveRetornarErroGenericoQuandoSenhaForInvalida() {
    LoginRequest request = new LoginRequest("ana@email.com", "senha-incorreta");
    MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    Usuario usuario = novoUsuario("ana@email.com", "{argon2}hash");

    when(usuarioRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(usuario));
    when(passwordEncoder.matches("senha-incorreta", "{argon2}hash")).thenReturn(false);

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> authService.login(request, httpRequest));

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    assertEquals("Credenciais invalidas", exception.getReason());
    verify(jwtService, never()).generateToken(any(), any());
    verify(sessaoUsuarioService, never()).criarSessao(any());
  }

  @Test
  @DisplayName("Deve emitir novo access token ao renovar sessao valida")
  void refreshDeveEmitirNovoAccessToken() {
    UUID usuarioId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    Usuario usuario = novoUsuario("ana@email.com", "{argon2}hash");

    when(sessaoUsuarioService.validarSessao("sessao-segura")).thenReturn(usuarioId);
    when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
    when(requestFingerprintService.generateFingerprint(httpRequest)).thenReturn("fingerprint");
    when(jwtService.generateToken(any(), any())).thenReturn("novo-jwt");

    JwtLoginResponse response = authService.refresh("sessao-segura", httpRequest);

    assertEquals("novo-jwt", response.accessToken());
    assertEquals("Bearer", response.tokenType());
  }

  @Test
  @DisplayName("Deve ignorar logout sem sessao")
  void logoutDeveIgnorarSessaoAusente() {
    authService.logout(null);

    verify(sessaoUsuarioService, never()).revogarSessao(any());
  }

  @Test
  @DisplayName("Deve ignorar logout quando sessao ja estiver revogada")
  void logoutDeveIgnorarSessaoJaRevogada() {
    doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessao invalida"))
        .when(sessaoUsuarioService)
        .revogarSessao("sessao-expirada");

    authService.logout("sessao-expirada");

    verify(sessaoUsuarioService).revogarSessao("sessao-expirada");
  }

  @Test
  @DisplayName("Deve propagar erro inesperado no logout")
  void logoutDevePropagarErroInesperado() {
    doThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha"))
        .when(sessaoUsuarioService)
        .revogarSessao("sessao-falhou");

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> authService.logout("sessao-falhou"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
  }

  @Test
  @DisplayName("Deve falhar ao renovar sessao quando usuario nao for encontrado")
  void refreshDeveFalharQuandoUsuarioNaoForEncontrado() {
    UUID usuarioId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    MockHttpServletRequest httpRequest = new MockHttpServletRequest();

    when(sessaoUsuarioService.validarSessao("sessao-segura")).thenReturn(usuarioId);
    when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class, () -> authService.refresh("sessao-segura", httpRequest));

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    verify(requestFingerprintService, never()).generateFingerprint(httpRequest);
  }

  private static Usuario novoUsuario(String email, String senha) {
    Usuario usuario = new Usuario();
    usuario.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    usuario.setEmail(email);
    usuario.setSenha(senha);
    return usuario;
  }
}
