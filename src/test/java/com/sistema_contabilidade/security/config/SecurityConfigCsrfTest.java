package com.sistema_contabilidade.security.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema_contabilidade.auth.dto.JwtLoginResponse;
import com.sistema_contabilidade.auth.service.AuthService;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import com.sistema_contabilidade.usuario.dto.UsuarioDto;
import com.sistema_contabilidade.usuario.service.UsuarioService;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("SecurityConfig CSRF tests")
class SecurityConfigCsrfTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private CustomUserDetailsService customUserDetailsService;
  @MockitoBean private UsuarioService usuarioService;

  @Test
  @DisplayName("Deve bloquear login sem token CSRF")
  void deveBloquearLoginSemTokenCsrf() throws Exception {
    when(authService.login(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new JwtLoginResponse("token", "Bearer"));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"admin@email.com",
                      "senha":"123"
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Deve permitir login com token CSRF")
  void devePermitirLoginComTokenCsrf() throws Exception {
    when(authService.login(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new JwtLoginResponse("token", "Bearer"));

    MvcResult csrfResult =
        mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
    JsonNode payload = new ObjectMapper().readTree(csrfResult.getResponse().getContentAsString());
    String csrfToken = payload.get("token").asText();
    Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .cookie(csrfCookie)
                .header("X-CSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"admin@email.com",
                      "senha":"123"
                    }
                    """))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Deve redirecionar para login ao acessar html protegido sem autenticacao")
  void deveRedirecionarParaLoginAoAcessarHtmlProtegidoSemAutenticacao() throws Exception {
    mockMvc
        .perform(get("/home.html"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  @DisplayName("Deve redirecionar para 404 ao acessar admin sem role admin")
  void deveRedirecionarPara404AoAcessarAdminSemRoleAdmin() throws Exception {
    var userDetails = User.withUsername("manager@email.com").password("x").roles("MANAGER").build();
    when(jwtService.extractUsername("token_manager")).thenReturn("manager@email.com");
    when(jwtService.isTokenValid("token_manager", userDetails)).thenReturn(true);
    when(customUserDetailsService.loadUserByUsername("manager@email.com")).thenReturn(userDetails);

    mockMvc
        .perform(get("/admin").cookie(new jakarta.servlet.http.Cookie("SC_TOKEN", "token_manager")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/404"));
  }

  @Test
  @DisplayName("Deve bloquear listagem de usuarios para perfil nao admin")
  void deveBloquearListagemDeUsuariosParaNaoAdmin() throws Exception {
    var userDetails = User.withUsername("manager@email.com").password("x").roles("MANAGER").build();
    when(jwtService.extractUsername("token_manager")).thenReturn("manager@email.com");
    when(jwtService.isTokenValid("token_manager", userDetails)).thenReturn(true);
    when(customUserDetailsService.loadUserByUsername("manager@email.com")).thenReturn(userDetails);

    mockMvc
        .perform(
            get("/api/v1/usuarios")
                .cookie(new jakarta.servlet.http.Cookie("SC_TOKEN", "token_manager")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Deve permitir atualizacao do proprio perfil para usuario autenticado")
  void devePermitirAtualizacaoDoProprioPerfil() throws Exception {
    var userDetails = User.withUsername("manager@email.com").password("x").roles("MANAGER").build();
    when(jwtService.extractUsername("token_manager")).thenReturn("manager@email.com");
    when(jwtService.isTokenValid("token_manager", userDetails)).thenReturn(true);
    when(customUserDetailsService.loadUserByUsername("manager@email.com")).thenReturn(userDetails);
    when(usuarioService.updatePerfil(
            org.mockito.ArgumentMatchers.eq("manager@email.com"),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new UsuarioDto(
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                "Manager",
                "manager@email.com",
                null));

    MvcResult csrfResult =
        mockMvc
            .perform(get("/api/v1/auth/csrf").cookie(new jakarta.servlet.http.Cookie("SC_TOKEN", "token_manager")))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode payload = new ObjectMapper().readTree(csrfResult.getResponse().getContentAsString());
    String csrfToken = payload.get("token").asText();
    Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

    mockMvc
        .perform(
            put("/api/v1/usuarios/me")
                .cookie(csrfCookie)
                .cookie(new jakarta.servlet.http.Cookie("SC_TOKEN", "token_manager"))
                .header("X-CSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "nome":"Manager",
                      "email":"manager@email.com",
                      "senha":"123456"
                    }
                    """))
        .andExpect(status().isOk());
  }
}
