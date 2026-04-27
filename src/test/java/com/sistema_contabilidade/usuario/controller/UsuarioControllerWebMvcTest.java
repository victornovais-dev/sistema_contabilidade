package com.sistema_contabilidade.usuario.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sistema_contabilidade.auth.service.SessaoUsuarioService;
import com.sistema_contabilidade.rbac.dto.RoleResumoDto;
import com.sistema_contabilidade.rbac.dto.UsuarioComRolesDto;
import com.sistema_contabilidade.security.service.AdminRouteService;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import com.sistema_contabilidade.security.service.RequestFingerprintService;
import com.sistema_contabilidade.usuario.dto.UsuarioCreateRequest;
import com.sistema_contabilidade.usuario.dto.UsuarioDto;
import com.sistema_contabilidade.usuario.dto.UsuarioUpdateByEmailRequest;
import com.sistema_contabilidade.usuario.service.UsuarioService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(UsuarioController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("UsuarioController WebMvc tests")
class UsuarioControllerWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UsuarioService usuarioService;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private CustomUserDetailsService customUserDetailsService;
  @MockitoBean private AdminRouteService adminRouteService;
  @MockitoBean private RequestFingerprintService requestFingerprintService;
  @MockitoBean private SessaoUsuarioService sessaoUsuarioService;

  @Test
  @DisplayName("Deve retornar lista de usuarios")
  void listarTodosDeveRetornarOk() throws Exception {
    List<UsuarioDto> usuarios =
        List.of(new UsuarioDto(UUID.randomUUID(), "Ana", "ana@email.com", null));
    when(usuarioService.listarTodos()).thenReturn(usuarios);

    mockMvc
        .perform(get("/api/v1/usuarios"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].nome").value("Ana"))
        .andExpect(jsonPath("$[0].email").value("ana@email.com"))
        .andExpect(jsonPath("$[0].senha").doesNotExist());
  }

  @Test
  @DisplayName("Deve criar usuario")
  void criarDeveRetornarCreated() throws Exception {
    UsuarioDto response = new UsuarioDto(UUID.randomUUID(), "Bia", "bia@email.com", null);
    when(usuarioService.save(org.mockito.ArgumentMatchers.any(UsuarioCreateRequest.class)))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "nome":"Bia",
                      "email":"bia@email.com",
                      "senha":"123456",
                      "role":"ADMIN"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.nome").value("Bia"))
        .andExpect(jsonPath("$.email").value("bia@email.com"))
        .andExpect(jsonPath("$.senha").doesNotExist());
  }

  @Test
  @DisplayName("Deve buscar usuario por id")
  void buscarPorIdDeveRetornarOk() throws Exception {
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    when(usuarioService.findById(id)).thenReturn(new UsuarioDto(id, "Carlos", "c@email.com", null));

    mockMvc
        .perform(get("/api/v1/usuarios/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nome").value("Carlos"));
  }

  @Test
  @DisplayName("Deve retornar 404 ao buscar usuario inexistente")
  void buscarPorIdDeveRetornarNotFound() throws Exception {
    UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
    when(usuarioService.findById(id))
        .thenThrow(
            new ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Usuario nao encontrado"));

    mockMvc.perform(get("/api/v1/usuarios/{id}", id)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Deve buscar usuario por email com roles")
  void buscarPorEmailDeveRetornarOk() throws Exception {
    UsuarioComRolesDto usuario =
        new UsuarioComRolesDto(
            UUID.randomUUID(),
            "Admin",
            "admin@email.com",
            Set.of(new RoleResumoDto(null, "ADMIN")));
    when(usuarioService.findComRolesByEmail("admin@email.com")).thenReturn(usuario);

    mockMvc
        .perform(get("/api/v1/usuarios/por-email").param("email", "admin@email.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("admin@email.com"))
        .andExpect(jsonPath("$.roles[*].nome").value(Matchers.hasItem("ADMIN")));
  }

  @Test
  @DisplayName("Deve atualizar usuario")
  void atualizarDeveRetornarOk() throws Exception {
    UUID id = UUID.fromString("33333333-3333-3333-3333-333333333333");
    UsuarioDto atualizado = new UsuarioDto(id, "Dani", "dani@email.com", null);
    when(usuarioService.update(
            org.mockito.ArgumentMatchers.eq(id),
            org.mockito.ArgumentMatchers.any(UsuarioDto.class)))
        .thenReturn(atualizado);

    mockMvc
        .perform(
            put("/api/v1/usuarios/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "id":"33333333-3333-3333-3333-333333333333",
                      "nome":"Dani",
                      "email":"dani@email.com"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("dani@email.com"))
        .andExpect(jsonPath("$.senha").doesNotExist());
  }

  @Test
  @DisplayName("Deve atualizar usuario por email com roles")
  void atualizarPorEmailDeveRetornarOk() throws Exception {
    UsuarioComRolesDto atualizado =
        new UsuarioComRolesDto(
            UUID.randomUUID(),
            "Dani",
            "dani@email.com",
            Set.of(new RoleResumoDto(null, "ADMIN"), new RoleResumoDto(null, "SUPPORT")));
    when(usuarioService.updateByEmail(
            org.mockito.ArgumentMatchers.any(UsuarioUpdateByEmailRequest.class)))
        .thenReturn(atualizado);

    mockMvc
        .perform(
            put("/api/v1/usuarios/por-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"dani@email.com",
                      "senha":"123456",
                      "roles":["ADMIN","SUPPORT"]
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("dani@email.com"))
        .andExpect(jsonPath("$.roles.length()").value(2));
  }

  @Test
  @DisplayName("Deve deletar usuario")
  void deletarDeveRetornarNoContent() throws Exception {
    UUID id = UUID.fromString("44444444-4444-4444-4444-444444444444");

    mockMvc.perform(delete("/api/v1/usuarios/{id}", id)).andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("Deve retornar 400 ao criar usuario invalido")
  void criarDeveRetornarBadRequestQuandoPayloadInvalido() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "nome":"",
                      "email":"email-invalido",
                      "senha":"",
                      "role":""
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"));
  }

  @Test
  @DisplayName("Deve retornar 400 ao criar usuario com SUPPORT e MANAGER ao mesmo tempo")
  void criarDeveRetornarBadRequestQuandoSupportEManagerForemInformadosJuntos() throws Exception {
    when(usuarioService.save(org.mockito.ArgumentMatchers.any(UsuarioCreateRequest.class)))
        .thenThrow(
            new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Usuario nao pode ter as roles SUPPORT e MANAGER ao mesmo tempo."));

    mockMvc
        .perform(
            post("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "nome":"Bia",
                      "email":"bia@email.com",
                      "senha":"123456",
                      "roles":["SUPPORT","MANAGER"]
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Deve atualizar o proprio perfil")
  void atualizarPerfilDeveRetornarOk() throws Exception {
    UUID id = UUID.fromString("33333333-3333-3333-3333-333333333333");
    UsuarioDto atualizado = new UsuarioDto(id, "Dani", "dani@email.com", null);
    when(usuarioService.updatePerfil(
            org.mockito.ArgumentMatchers.nullable(String.class),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(atualizado);

    mockMvc
        .perform(
            put("/api/v1/usuarios/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "nome":"Dani",
                      "email":"dani@email.com",
                      "senha":"123456"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("dani@email.com"))
        .andExpect(jsonPath("$.senha").doesNotExist());
  }
}
