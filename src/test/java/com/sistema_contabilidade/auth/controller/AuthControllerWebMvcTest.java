package com.sistema_contabilidade.auth.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sistema_contabilidade.auth.dto.AuthenticatedLoginResult;
import com.sistema_contabilidade.auth.dto.JwtLoginResponse;
import com.sistema_contabilidade.auth.service.AuthLoginChallenge;
import com.sistema_contabilidade.auth.service.AuthService;
import com.sistema_contabilidade.auth.service.LoginChallengeCookieService;
import com.sistema_contabilidade.auth.service.LoginFlowResult;
import com.sistema_contabilidade.auth.service.SessaoUsuarioService;
import com.sistema_contabilidade.security.service.AdminRouteService;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import com.sistema_contabilidade.security.service.RequestFingerprintService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthController WebMvc tests")
class AuthControllerWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private CustomUserDetailsService customUserDetailsService;
  @MockitoBean private SessaoUsuarioService sessaoUsuarioService;
  @MockitoBean private AdminRouteService adminRouteService;
  @MockitoBean private RequestFingerprintService requestFingerprintService;
  @MockitoBean private LoginChallengeCookieService loginChallengeCookieService;

  @Test
  @DisplayName("Deve autenticar no endpoint login")
  void loginDeveRetornarOk() throws Exception {
    when(authService.login(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            LoginFlowResult.authenticated(
                new AuthenticatedLoginResult(
                    new JwtLoginResponse("token-jwt", "Bearer"), "sessao-token")));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"ana@email.com",
                      "senha":"123456"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("token-jwt"))
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(
            header()
                .string(
                    HttpHeaders.SET_COOKIE,
                    org.hamcrest.Matchers.containsString("SC_SESSION=sessao-token")));
  }

  @Test
  @DisplayName("Deve responder challenge de nova senha no endpoint login")
  void loginDeveRetornarChallengeDeNovaSenha() throws Exception {
    AuthLoginChallenge challenge =
        new AuthLoginChallenge(
            com.sistema_contabilidade.auth.config.AuthProvider.COGNITO,
            "NEW_PASSWORD_REQUIRED",
            "ana@email.com",
            "challenge-session",
            "Primeiro acesso detectado. Defina uma nova senha para continuar.");
    when(authService.login(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(LoginFlowResult.challenge(challenge));
    when(loginChallengeCookieService.createToken(challenge)).thenReturn("challenge-cookie");

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"ana@email.com",
                      "senha":"123456"
                    }
                    """))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.challengeRequired").value(true))
        .andExpect(jsonPath("$.challengeName").value("NEW_PASSWORD_REQUIRED"))
        .andExpect(
            header()
                .string(
                    HttpHeaders.SET_COOKIE,
                    org.hamcrest.Matchers.containsString("SC_LOGIN_CHALLENGE=challenge-cookie")));
  }
}
