package com.sistema_contabilidade.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.auth.config.AuthProvider;
import com.sistema_contabilidade.auth.dto.AuthenticatedLoginResult;
import com.sistema_contabilidade.auth.dto.JwtLoginResponse;
import com.sistema_contabilidade.auth.dto.LoginRequest;
import com.sistema_contabilidade.auth.model.SessaoUsuario;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import com.sistema_contabilidade.security.service.RequestFingerprintService;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService unit tests")
class AuthServiceTest {

  @Mock private JwtService jwtService;
  @Mock private UsuarioRepository usuarioRepository;
  @Mock private CustomUserDetailsService customUserDetailsService;
  @Mock private SessaoUsuarioService sessaoUsuarioService;
  @Mock private RequestFingerprintService requestFingerprintService;
  @Mock private AuthProviderStrategyResolver authProviderStrategyResolver;
  @Mock private AuthProviderStrategy localAuthProviderStrategy;
  @Mock private AuthProviderStrategy cognitoAuthProviderStrategy;
  @Mock private ObjectProvider<CognitoIdentitySyncService> cognitoIdentitySyncServiceProvider;
  @Mock private ObjectProvider<CognitoRoleSyncService> cognitoRoleSyncServiceProvider;
  @Mock private CognitoIdentitySyncService cognitoIdentitySyncService;
  @Mock private CognitoRoleSyncService cognitoRoleSyncService;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService =
        new AuthService(
            jwtService,
            usuarioRepository,
            customUserDetailsService,
            sessaoUsuarioService,
            requestFingerprintService,
            authProviderStrategyResolver,
            cognitoIdentitySyncServiceProvider,
            cognitoRoleSyncServiceProvider);
    ReflectionTestUtils.setField(authService, "loginDiagnosticsEnabled", false);
  }

  @Test
  @DisplayName("Deve autenticar com provider local e retornar access token com sessao opaca")
  void loginDeveRetornarAccessTokenESessao() {
    LoginRequest request = new LoginRequest("ana@email.com", "123456");
    MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    Usuario usuario = novoUsuario("ana@email.com", "{argon2}hash-atual");
    UserDetails userDetails =
        User.withUsername("ana@email.com").password("hash").authorities("ROLE_ADMIN").build();

    when(authProviderStrategyResolver.current()).thenReturn(localAuthProviderStrategy);
    when(localAuthProviderStrategy.login(request))
        .thenReturn(
            new AuthProviderLoginResult(
                AuthProvider.LOCAL,
                usuario.getId(),
                usuario.getEmail(),
                usuario.getEmail(),
                usuario.getNome(),
                null,
                Set.of(),
                null,
                null));
    when(usuarioRepository.findWithRolesById(usuario.getId())).thenReturn(Optional.of(usuario));
    when(sessaoUsuarioService.criarSessao(any(SessionCreationRequest.class)))
        .thenReturn("sessao-segura");
    when(customUserDetailsService.loadUserById(usuario.getId())).thenReturn(userDetails);
    when(requestFingerprintService.generateFingerprint(httpRequest)).thenReturn("fingerprint");
    when(jwtService.generateToken(userDetails, usuario.getId(), null, "fingerprint"))
        .thenReturn("jwt-token");

    LoginFlowResult response = authService.login(request, httpRequest);

    assertEquals("jwt-token", response.authenticatedResult().response().accessToken());
    assertEquals("Bearer", response.authenticatedResult().response().tokenType());
    assertEquals("sessao-segura", response.authenticatedResult().sessionToken());
    verify(customUserDetailsService).aquecerCacheUsuario(usuario);
    verify(sessaoUsuarioService).revogarSessoesAtivas(usuario.getId());
  }

  @Test
  @DisplayName("Deve autenticar com provider Cognito, sincronizar usuario e roles locais")
  void loginCognitoDeveSincronizarUsuarioERoles() {
    LoginRequest request = new LoginRequest("ana@email.com", "123456");
    MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    Usuario usuario = novoUsuario("ana@email.com", "{argon2}placeholder");
    usuario.setCognitoSub("sub-123");
    UserDetails userDetails =
        User.withUsername("ana@email.com").password("hash").authorities("ROLE_ADMIN").build();
    AuthProviderLoginResult loginResult =
        new AuthProviderLoginResult(
            AuthProvider.COGNITO,
            null,
            "ana@email.com",
            "ana@email.com",
            "Ana",
            "sub-123",
            Set.of("ADMIN"),
            "refresh-token",
            null);

    when(authProviderStrategyResolver.current()).thenReturn(cognitoAuthProviderStrategy);
    when(cognitoAuthProviderStrategy.login(request)).thenReturn(loginResult);
    when(cognitoIdentitySyncServiceProvider.getIfAvailable())
        .thenReturn(cognitoIdentitySyncService);
    when(cognitoRoleSyncServiceProvider.getIfAvailable()).thenReturn(cognitoRoleSyncService);
    when(cognitoIdentitySyncService.synchronizeLoginIdentity(loginResult)).thenReturn(usuario);
    when(cognitoRoleSyncService.syncMemberships(usuario, Set.of("ADMIN")))
        .thenReturn(new CognitoRoleSyncResult(Set.of("ADMIN"), "hash-groups"));
    when(usuarioRepository.save(usuario)).thenReturn(usuario);
    when(sessaoUsuarioService.criarSessao(any(SessionCreationRequest.class)))
        .thenReturn("sessao-cognito");
    when(customUserDetailsService.loadUserById(usuario.getId())).thenReturn(userDetails);
    when(requestFingerprintService.generateFingerprint(httpRequest)).thenReturn("fingerprint");
    when(jwtService.generateToken(userDetails, usuario.getId(), "sub-123", "fingerprint"))
        .thenReturn("jwt-cognito");

    LoginFlowResult response = authService.login(request, httpRequest);

    assertEquals("jwt-cognito", response.authenticatedResult().response().accessToken());
    assertEquals("sessao-cognito", response.authenticatedResult().sessionToken());
    verify(cognitoIdentitySyncService).synchronizeLoginIdentity(loginResult);
    verify(cognitoRoleSyncService).syncMemberships(usuario, Set.of("ADMIN"));
  }

  @Test
  @DisplayName("Deve sinalizar challenge de troca inicial de senha quando Cognito exigir")
  void loginCognitoDeveRetornarChallengeDeNovaSenha() {
    LoginRequest request = new LoginRequest("ana@email.com", "123456");
    MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    AuthLoginChallenge challenge =
        new AuthLoginChallenge(
            AuthProvider.COGNITO,
            "NEW_PASSWORD_REQUIRED",
            "ana@email.com",
            "challenge-session",
            "Primeiro acesso detectado. Defina uma nova senha para continuar.");

    when(authProviderStrategyResolver.current()).thenReturn(cognitoAuthProviderStrategy);
    when(cognitoAuthProviderStrategy.login(request))
        .thenReturn(
            new AuthProviderLoginResult(
                AuthProvider.COGNITO,
                null,
                "ana@email.com",
                "ana@email.com",
                null,
                null,
                Set.of(),
                null,
                challenge));

    LoginFlowResult response = authService.login(request, httpRequest);

    assertEquals("NEW_PASSWORD_REQUIRED", response.challenge().challengeName());
    verify(sessaoUsuarioService, never()).criarSessao(any(SessionCreationRequest.class));
  }

  @Test
  @DisplayName("Deve concluir troca inicial de senha e autenticar usuario Cognito")
  void completeNewPasswordDeveAutenticarUsuario() {
    MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    Usuario usuario = novoUsuario("ana@email.com", "{argon2}placeholder");
    usuario.setCognitoSub("sub-123");
    UserDetails userDetails =
        User.withUsername("ana@email.com").password("hash").authorities("ROLE_ADMIN").build();
    AuthLoginChallenge challenge =
        new AuthLoginChallenge(
            AuthProvider.COGNITO,
            "NEW_PASSWORD_REQUIRED",
            "ana@email.com",
            "challenge-session",
            "Primeiro acesso detectado. Defina uma nova senha para continuar.");
    AuthProviderLoginResult loginResult =
        new AuthProviderLoginResult(
            AuthProvider.COGNITO,
            null,
            "ana@email.com",
            "ana@email.com",
            "Ana",
            "sub-123",
            Set.of("ADMIN"),
            "refresh-token",
            null);

    when(authProviderStrategyResolver.resolve(AuthProvider.COGNITO))
        .thenReturn(cognitoAuthProviderStrategy);
    when(cognitoAuthProviderStrategy.completeNewPassword(
            challenge,
            new com.sistema_contabilidade.auth.dto.CompleteNewPasswordRequest("Nova@123")))
        .thenReturn(loginResult);
    when(cognitoIdentitySyncServiceProvider.getIfAvailable())
        .thenReturn(cognitoIdentitySyncService);
    when(cognitoRoleSyncServiceProvider.getIfAvailable()).thenReturn(cognitoRoleSyncService);
    when(cognitoIdentitySyncService.synchronizeLoginIdentity(loginResult)).thenReturn(usuario);
    when(cognitoRoleSyncService.syncMemberships(usuario, Set.of("ADMIN")))
        .thenReturn(new CognitoRoleSyncResult(Set.of("ADMIN"), "hash-groups"));
    when(usuarioRepository.save(usuario)).thenReturn(usuario);
    when(sessaoUsuarioService.criarSessao(any(SessionCreationRequest.class)))
        .thenReturn("sessao-cognito");
    when(customUserDetailsService.loadUserById(usuario.getId())).thenReturn(userDetails);
    when(requestFingerprintService.generateFingerprint(httpRequest)).thenReturn("fingerprint");
    when(jwtService.generateToken(userDetails, usuario.getId(), "sub-123", "fingerprint"))
        .thenReturn("jwt-cognito");

    AuthenticatedLoginResult response =
        authService.completeNewPassword(
            challenge,
            new com.sistema_contabilidade.auth.dto.CompleteNewPasswordRequest("Nova@123"),
            httpRequest);

    assertEquals("jwt-cognito", response.response().accessToken());
    assertEquals("sessao-cognito", response.sessionToken());
  }

  @Test
  @DisplayName("Deve emitir novo access token ao renovar sessao local valida")
  void refreshDeveEmitirNovoAccessToken() {
    UUID usuarioId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    Usuario usuario = novoUsuario("ana@email.com", "{argon2}hash");
    UserDetails userDetails =
        User.withUsername("ana@email.com").password("hash").authorities("ROLE_ADMIN").build();
    SessaoUsuario sessaoUsuario = sessaoLocal(usuarioId);

    when(sessaoUsuarioService.obterSessaoAtiva("sessao-segura")).thenReturn(sessaoUsuario);
    when(authProviderStrategyResolver.resolve(AuthProvider.LOCAL))
        .thenReturn(localAuthProviderStrategy);
    when(localAuthProviderStrategy.refresh(sessaoUsuario))
        .thenReturn(
            new AuthProviderRefreshResult(
                AuthProvider.LOCAL, "ana@email.com", "ana@email.com", "Ana", null, Set.of(), null));
    when(usuarioRepository.findWithRolesById(usuarioId)).thenReturn(Optional.of(usuario));
    when(customUserDetailsService.loadUserById(usuarioId)).thenReturn(userDetails);
    when(requestFingerprintService.generateFingerprint(httpRequest)).thenReturn("fingerprint");
    when(jwtService.generateToken(userDetails, usuarioId, null, "fingerprint"))
        .thenReturn("novo-jwt");

    JwtLoginResponse response = authService.refresh("sessao-segura", httpRequest);

    assertEquals("novo-jwt", response.accessToken());
    assertEquals("Bearer", response.tokenType());
  }

  @Test
  @DisplayName("Deve atualizar sessao Cognito ao renovar token")
  void refreshCognitoDeveAtualizarSessao() {
    UUID usuarioId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    Usuario usuario = novoUsuario("ana@email.com", "{argon2}hash");
    usuario.setCognitoSub("sub-123");
    UserDetails userDetails =
        User.withUsername("ana@email.com").password("hash").authorities("ROLE_ADMIN").build();
    SessaoUsuario sessaoUsuario = sessaoCognito(usuarioId);
    AuthProviderRefreshResult refreshResult =
        new AuthProviderRefreshResult(
            AuthProvider.COGNITO,
            "ana@email.com",
            "ana@email.com",
            "Ana",
            "sub-123",
            Set.of("ADMIN"),
            null);

    when(sessaoUsuarioService.obterSessaoAtiva("sessao-cognito")).thenReturn(sessaoUsuario);
    when(authProviderStrategyResolver.resolve(AuthProvider.COGNITO))
        .thenReturn(cognitoAuthProviderStrategy);
    when(cognitoAuthProviderStrategy.refresh(sessaoUsuario)).thenReturn(refreshResult);
    when(cognitoIdentitySyncServiceProvider.getIfAvailable())
        .thenReturn(cognitoIdentitySyncService);
    when(cognitoRoleSyncServiceProvider.getIfAvailable()).thenReturn(cognitoRoleSyncService);
    when(cognitoIdentitySyncService.synchronizeRefreshIdentity(refreshResult)).thenReturn(usuario);
    when(cognitoRoleSyncService.syncMemberships(usuario, Set.of("ADMIN")))
        .thenReturn(new CognitoRoleSyncResult(Set.of("ADMIN"), "hash-groups"));
    when(usuarioRepository.save(usuario)).thenReturn(usuario);
    when(sessaoUsuarioService.atualizarSessao(sessaoUsuario, refreshResult, "hash-groups"))
        .thenReturn(sessaoUsuario);
    when(customUserDetailsService.loadUserById(usuarioId)).thenReturn(userDetails);
    when(requestFingerprintService.generateFingerprint(httpRequest)).thenReturn("fingerprint");
    when(jwtService.generateToken(userDetails, usuarioId, "sub-123", "fingerprint"))
        .thenReturn("novo-jwt");

    JwtLoginResponse response = authService.refresh("sessao-cognito", httpRequest);

    assertEquals("novo-jwt", response.accessToken());
    verify(sessaoUsuarioService).atualizarSessao(sessaoUsuario, refreshResult, "hash-groups");
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
        .obterSessaoAtiva("sessao-expirada");

    authService.logout("sessao-expirada");

    verify(sessaoUsuarioService).obterSessaoAtiva("sessao-expirada");
  }

  @Test
  @DisplayName("Deve propagar erro inesperado no logout")
  void logoutDevePropagarErroInesperado() {
    doThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha"))
        .when(sessaoUsuarioService)
        .obterSessaoAtiva("sessao-falhou");

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> authService.logout("sessao-falhou"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
  }

  private static Usuario novoUsuario(String email, String senha) {
    Usuario usuario = new Usuario();
    usuario.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    usuario.setNome("Ana");
    usuario.setEmail(email);
    usuario.setSenha(senha);
    return usuario;
  }

  private SessaoUsuario sessaoLocal(UUID usuarioId) {
    SessaoUsuario sessaoUsuario = new SessaoUsuario();
    sessaoUsuario.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    sessaoUsuario.setUsuarioId(usuarioId);
    sessaoUsuario.setAuthProvider(AuthProvider.LOCAL);
    sessaoUsuario.setAuthUsername("ana@email.com");
    sessaoUsuario.setCriadaEm(LocalDateTime.now());
    sessaoUsuario.setAtualizadaEm(LocalDateTime.now());
    sessaoUsuario.setExpiraEm(LocalDateTime.now().plusMinutes(30));
    return sessaoUsuario;
  }

  private SessaoUsuario sessaoCognito(UUID usuarioId) {
    SessaoUsuario sessaoUsuario = sessaoLocal(usuarioId);
    sessaoUsuario.setAuthProvider(AuthProvider.COGNITO);
    sessaoUsuario.setCognitoSub("sub-123");
    return sessaoUsuario;
  }
}
