package com.sistema_contabilidade.security.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sistema_contabilidade.auth.dto.AuthenticatedLoginResult;
import com.sistema_contabilidade.auth.dto.JwtLoginResponse;
import com.sistema_contabilidade.auth.service.AuthService;
import com.sistema_contabilidade.auth.service.SessaoUsuarioService;
import com.sistema_contabilidade.relatorio.service.RelatorioFinanceiroService;
import com.sistema_contabilidade.security.service.AdminRouteService;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import com.sistema_contabilidade.usuario.dto.UsuarioDto;
import com.sistema_contabilidade.usuario.service.UsuarioService;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("SecurityConfig CSRF tests")
class SecurityConfigCsrfTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AdminRouteService adminRouteService;

  @MockitoBean private AuthService authService;
  @MockitoBean private RelatorioFinanceiroService relatorioFinanceiroService;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private CustomUserDetailsService customUserDetailsService;
  @MockitoBean private SessaoUsuarioService sessaoUsuarioService;
  @MockitoBean private UsuarioService usuarioService;

  @Test
  @DisplayName("Deve bloquear login sem token CSRF")
  void deveBloquearLoginSemTokenCsrf() throws Exception {
    when(authService.login(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new AuthenticatedLoginResult(new JwtLoginResponse("token", "Bearer"), "sessao"));

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
    when(authService.login(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new AuthenticatedLoginResult(new JwtLoginResponse("token", "Bearer"), "sessao"));

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
    when(jwtService.isTokenValid(
            org.mockito.ArgumentMatchers.eq("token_manager"),
            org.mockito.ArgumentMatchers.eq(userDetails),
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(true);
    when(customUserDetailsService.loadUserByUsername("manager@email.com")).thenReturn(userDetails);

    mockMvc
        .perform(get("/admin").cookie(new jakarta.servlet.http.Cookie("SC_TOKEN", "token_manager")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/404"));
  }

  @Test
  @DisplayName("Deve redirecionar para 404 ao acessar gerenciar roles sem role admin")
  void deveRedirecionarPara404AoAcessarGerenciarRolesSemRoleAdmin() throws Exception {
    var userDetails = User.withUsername("manager@email.com").password("x").roles("MANAGER").build();
    when(jwtService.extractUsername("token_manager")).thenReturn("manager@email.com");
    when(jwtService.isTokenValid(
            org.mockito.ArgumentMatchers.eq("token_manager"),
            org.mockito.ArgumentMatchers.eq(userDetails),
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(true);
    when(customUserDetailsService.loadUserByUsername("manager@email.com")).thenReturn(userDetails);

    mockMvc
        .perform(
            get("/gerenciar_roles")
                .cookie(new jakarta.servlet.http.Cookie("SC_TOKEN", "token_manager")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/404"));
  }

  @Test
  @DisplayName("Deve bloquear listagem de usuarios para perfil nao admin")
  void deveBloquearListagemDeUsuariosParaNaoAdmin() throws Exception {
    var userDetails = User.withUsername("manager@email.com").password("x").roles("MANAGER").build();
    when(jwtService.extractUsername("token_manager")).thenReturn("manager@email.com");
    when(jwtService.isTokenValid(
            org.mockito.ArgumentMatchers.eq("token_manager"),
            org.mockito.ArgumentMatchers.eq(userDetails),
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(true);
    when(customUserDetailsService.loadUserByUsername("manager@email.com")).thenReturn(userDetails);

    mockMvc
        .perform(
            get(adminRouteService.adminUserApiBasePath())
                .cookie(new jakarta.servlet.http.Cookie("SC_TOKEN", "token_manager")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Deve permitir atualizacao do proprio perfil para usuario autenticado")
  void devePermitirAtualizacaoDoProprioPerfil() throws Exception {
    var userDetails = User.withUsername("manager@email.com").password("x").roles("MANAGER").build();
    when(jwtService.extractUsername("token_manager")).thenReturn("manager@email.com");
    when(jwtService.isTokenValid(
            org.mockito.ArgumentMatchers.eq("token_manager"),
            org.mockito.ArgumentMatchers.eq(userDetails),
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(true);
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
            .perform(
                get("/api/v1/auth/csrf")
                    .cookie(new jakarta.servlet.http.Cookie("SC_TOKEN", "token_manager")))
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

  @Test
  @DisplayName("Deve permitir roles de relatorio para usuario autenticado com multiplas roles")
  void devePermitirRolesDeRelatorioParaUsuarioAutenticadoComMultiplasRoles() throws Exception {
    var userDetails =
        User.withUsername("multi@email.com").password("x").roles("MANAGER", "OPERADOR").build();
    when(jwtService.extractUsername("token_multi")).thenReturn("multi@email.com");
    when(jwtService.isTokenValid(
            org.mockito.ArgumentMatchers.eq("token_multi"),
            org.mockito.ArgumentMatchers.eq(userDetails),
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(true);
    when(customUserDetailsService.loadUserByUsername("multi@email.com")).thenReturn(userDetails);
    when(relatorioFinanceiroService.listarRolesDisponiveis(org.mockito.ArgumentMatchers.any()))
        .thenReturn(java.util.List.of("MANAGER", "OPERADOR"));

    mockMvc
        .perform(
            get("/api/v1/relatorios/roles")
                .cookie(new jakarta.servlet.http.Cookie("SC_TOKEN", "token_multi")))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Deve responder 404 ao acessar rota admin legada de API")
  void deveResponder404AoAcessarRotaAdminLegadaDeApi() throws Exception {
    mockMvc.perform(get("/api/v1/admin/roles")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Deve permitir adicionar comprovante para perfil nao contabil")
  void devePermitirAdicionarComprovanteParaPerfilNaoContabil() throws Exception {
    var userDetails = User.withUsername("manager@email.com").password("x").roles("MANAGER").build();
    when(jwtService.extractUsername("token_manager")).thenReturn("manager@email.com");
    when(jwtService.isTokenValid(
            org.mockito.ArgumentMatchers.eq("token_manager"),
            org.mockito.ArgumentMatchers.eq(userDetails),
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(true);
    when(customUserDetailsService.loadUserByUsername("manager@email.com")).thenReturn(userDetails);

    mockMvc
        .perform(
            get("/adicionar_comprovante")
                .cookie(new jakarta.servlet.http.Cookie("SC_TOKEN", "token_manager")))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Deve redirecionar para 404 ao acessar adicionar comprovante como contabil")
  void deveRedirecionarPara404AoAcessarAdicionarComprovanteComoContabil() throws Exception {
    var userDetails =
        User.withUsername("contabil@email.com").password("x").roles("CONTABIL").build();
    when(jwtService.extractUsername("token_contabil")).thenReturn("contabil@email.com");
    when(jwtService.isTokenValid(
            org.mockito.ArgumentMatchers.eq("token_contabil"),
            org.mockito.ArgumentMatchers.eq(userDetails),
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(true);
    when(customUserDetailsService.loadUserByUsername("contabil@email.com")).thenReturn(userDetails);

    mockMvc
        .perform(
            get("/adicionar_comprovante")
                .cookie(new jakarta.servlet.http.Cookie("SC_TOKEN", "token_contabil")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/404"));
  }
}
