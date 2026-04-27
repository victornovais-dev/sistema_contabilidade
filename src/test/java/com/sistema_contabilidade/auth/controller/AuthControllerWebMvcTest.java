package com.sistema_contabilidade.auth.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sistema_contabilidade.auth.dto.AuthenticatedLoginResult;
import com.sistema_contabilidade.auth.dto.JwtLoginResponse;
import com.sistema_contabilidade.auth.service.AuthService;
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

  @Test
  @DisplayName("Deve autenticar no endpoint login")
  void loginDeveRetornarOk() throws Exception {
    when(authService.login(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new AuthenticatedLoginResult(
                new JwtLoginResponse("token-jwt", "Bearer"), "sessao-token"));

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
}
