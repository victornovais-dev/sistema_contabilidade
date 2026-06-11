package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.config.AuthProvider;
import com.sistema_contabilidade.auth.dto.AuthenticatedLoginResult;
import com.sistema_contabilidade.auth.dto.CompleteNewPasswordRequest;
import com.sistema_contabilidade.auth.dto.JwtLoginResponse;
import com.sistema_contabilidade.auth.dto.LoginRequest;
import com.sistema_contabilidade.auth.model.SessaoUsuario;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import com.sistema_contabilidade.security.service.RequestFingerprintService;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

  private static final String TOKEN_TYPE = "Bearer";

  private final JwtService jwtService;
  private final UsuarioRepository usuarioRepository;
  private final CustomUserDetailsService customUserDetailsService;
  private final SessaoUsuarioService sessaoUsuarioService;
  private final RequestFingerprintService requestFingerprintService;
  private final AuthProviderStrategyResolver authProviderStrategyResolver;
  private final ObjectProvider<CognitoIdentitySyncService> cognitoIdentitySyncServiceProvider;
  private final ObjectProvider<CognitoRoleSyncService> cognitoRoleSyncServiceProvider;

  @Value("${app.auth.login-diagnostics.enabled:false}")
  private boolean loginDiagnosticsEnabled;

  public LoginFlowResult login(LoginRequest request, HttpServletRequest httpRequest) {
    long t0 = System.currentTimeMillis();
    AuthProviderLoginResult authResult = authProviderStrategyResolver.current().login(request);
    long t1 = System.currentTimeMillis();
    if (authResult.challengeRequired()) {
      return LoginFlowResult.challenge(authResult.challenge());
    }
    return LoginFlowResult.authenticated(
        completeAuthenticatedLogin(authResult, httpRequest, t0, t1));
  }

  public AuthenticatedLoginResult completeNewPassword(
      AuthLoginChallenge challenge,
      CompleteNewPasswordRequest request,
      HttpServletRequest httpRequest) {
    AuthProviderLoginResult authResult =
        authProviderStrategyResolver
            .resolve(challenge.provider())
            .completeNewPassword(challenge, request);
    if (authResult.challengeRequired()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Cognito retornou challenge adicional nao suportado");
    }
    return completeAuthenticatedLogin(
        authResult, httpRequest, System.currentTimeMillis(), System.currentTimeMillis());
  }

  private AuthenticatedLoginResult completeAuthenticatedLogin(
      AuthProviderLoginResult authResult, HttpServletRequest httpRequest, long t0, long t1) {
    ResolvedUserState resolvedUserState = resolveLoginUser(authResult);
    Usuario usuario = resolvedUserState.usuario();
    customUserDetailsService.aquecerCacheUsuario(usuario);
    sessaoUsuarioService.revogarSessoesAtivas(usuario.getId());
    String sessionToken =
        sessaoUsuarioService.criarSessao(
            new SessionCreationRequest(
                usuario.getId(),
                authResult.provider(),
                authResult.providerUsername(),
                authResult.cognitoSub(),
                authResult.refreshToken(),
                resolvedUserState.groups(),
                resolvedUserState.groupsHash()));
    long t2 = System.currentTimeMillis();

    UserDetails userDetails = customUserDetailsService.loadUserById(usuario.getId());
    String token =
        jwtService.generateToken(
            userDetails,
            usuario.getId(),
            usuario.getCognitoSub(),
            requestFingerprintService.generateFingerprint(httpRequest));
    long t3 = System.currentTimeMillis();
    logDiagnostico(t0, t1, t2, t3);
    return new AuthenticatedLoginResult(new JwtLoginResponse(token, TOKEN_TYPE), sessionToken);
  }

  public JwtLoginResponse refresh(String sessionToken, HttpServletRequest httpRequest) {
    SessaoUsuario sessaoUsuario = sessaoUsuarioService.obterSessaoAtiva(sessionToken);
    AuthProvider provider = sessaoUsuario.getAuthProvider();
    AuthProviderRefreshResult refreshResult =
        authProviderStrategyResolver.resolve(provider).refresh(sessaoUsuario);

    ResolvedUserState resolvedUserState = resolveRefreshUser(sessaoUsuario, refreshResult);
    Usuario usuario = resolvedUserState.usuario();
    customUserDetailsService.aquecerCacheUsuario(usuario);

    if (provider == AuthProvider.COGNITO) {
      sessaoUsuarioService.atualizarSessao(
          sessaoUsuario, refreshResult, resolvedUserState.groupsHash());
    }

    UserDetails userDetails = customUserDetailsService.loadUserById(usuario.getId());
    String token =
        jwtService.generateToken(
            userDetails,
            usuario.getId(),
            usuario.getCognitoSub(),
            requestFingerprintService.generateFingerprint(httpRequest));
    return new JwtLoginResponse(token, TOKEN_TYPE);
  }

  public void logout(String sessionToken) {
    if (sessionToken == null || sessionToken.isBlank()) {
      return;
    }
    try {
      SessaoUsuario sessaoUsuario = sessaoUsuarioService.obterSessaoAtiva(sessionToken);
      authProviderStrategyResolver.resolve(sessaoUsuario.getAuthProvider()).logout(sessaoUsuario);
      sessaoUsuarioService.revogarSessao(sessionToken);
    } catch (ResponseStatusException exception) {
      if (exception.getStatusCode().value() != HttpStatus.UNAUTHORIZED.value()) {
        throw exception;
      }
    }
  }

  private ResolvedUserState resolveLoginUser(AuthProviderLoginResult authResult) {
    if (authResult.provider() == AuthProvider.LOCAL) {
      Usuario usuario = loadUserById(authResult.localUserId());
      return new ResolvedUserState(usuario, Set.of(), null);
    }

    CognitoIdentitySyncService identitySyncService = requireCognitoIdentitySyncService();
    CognitoRoleSyncService roleSyncService = requireCognitoRoleSyncService();
    Usuario usuario = identitySyncService.synchronizeLoginIdentity(authResult);
    CognitoRoleSyncResult syncResult =
        roleSyncService.syncMemberships(usuario, authResult.groups());
    usuario = usuarioRepository.save(usuario);
    return new ResolvedUserState(usuario, syncResult.normalizedGroups(), syncResult.groupsHash());
  }

  private ResolvedUserState resolveRefreshUser(
      SessaoUsuario sessaoUsuario, AuthProviderRefreshResult refreshResult) {
    if (sessaoUsuario.getAuthProvider() == AuthProvider.LOCAL) {
      return new ResolvedUserState(loadUserById(sessaoUsuario.getUsuarioId()), Set.of(), null);
    }

    CognitoIdentitySyncService identitySyncService = requireCognitoIdentitySyncService();
    CognitoRoleSyncService roleSyncService = requireCognitoRoleSyncService();
    Usuario usuario = identitySyncService.synchronizeRefreshIdentity(refreshResult);
    CognitoRoleSyncResult syncResult =
        roleSyncService.syncMemberships(usuario, refreshResult.groups());
    usuario = usuarioRepository.save(usuario);
    return new ResolvedUserState(usuario, syncResult.normalizedGroups(), syncResult.groupsHash());
  }

  private Usuario loadUserById(java.util.UUID usuarioId) {
    return usuarioRepository
        .findWithRolesById(usuarioId)
        .orElseThrow(AuthFailureSupport::invalidCredentials);
  }

  private CognitoIdentitySyncService requireCognitoIdentitySyncService() {
    CognitoIdentitySyncService service = cognitoIdentitySyncServiceProvider.getIfAvailable();
    if (service == null) {
      throw new IllegalStateException("CognitoIdentitySyncService indisponivel");
    }
    return service;
  }

  private CognitoRoleSyncService requireCognitoRoleSyncService() {
    CognitoRoleSyncService service = cognitoRoleSyncServiceProvider.getIfAvailable();
    if (service == null) {
      throw new IllegalStateException("CognitoRoleSyncService indisponivel");
    }
    return service;
  }

  private void logDiagnostico(long t0, long t1, long t2, long t3) {
    if (loginDiagnosticsEnabled && log.isInfoEnabled()) {
      log.info(
          "Provider={}ms | Sync/Sessao={}ms | JWT={}ms | Total={}ms",
          (t1 - t0),
          (t2 - t1),
          (t3 - t2),
          (t3 - t0));
    }
  }

  private record ResolvedUserState(Usuario usuario, Set<String> groups, String groupsHash) {}
}
