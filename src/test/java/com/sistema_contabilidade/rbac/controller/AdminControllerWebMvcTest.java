package com.sistema_contabilidade.rbac.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sistema_contabilidade.rbac.dto.PermissaoDto;
import com.sistema_contabilidade.rbac.dto.RoleDto;
import com.sistema_contabilidade.rbac.dto.RoleResumoDto;
import com.sistema_contabilidade.rbac.dto.UsuarioComRolesDto;
import com.sistema_contabilidade.rbac.service.RoleService;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminController WebMvc tests")
class AdminControllerWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RoleService roleService;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private CustomUserDetailsService customUserDetailsService;

  @Test
  @DisplayName("Deve criar role")
  void criarRoleDeveRetornarOk() throws Exception {
    RoleDto roleDto = new RoleDto(UUID.randomUUID(), "ADMIN", Set.of());
    when(roleService.criarRole("ADMIN")).thenReturn(roleDto);

    mockMvc
        .perform(
            post("/api/v1/admin/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "nome":"ADMIN"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nome").value("ADMIN"));
  }

  @Test
  @DisplayName("Deve criar permissao")
  void criarPermissaoDeveRetornarOk() throws Exception {
    PermissaoDto permissao = new PermissaoDto(UUID.randomUUID(), "USER_READ");
    when(roleService.criarPermissao("USER_READ")).thenReturn(permissao);

    mockMvc
        .perform(
            post("/api/v1/admin/permissoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "nome":"USER_READ"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nome").value("USER_READ"));
  }

  @Test
  @DisplayName("Deve adicionar permissao na role")
  void adicionarPermissaoDeveRetornarOk() throws Exception {
    RoleDto roleDto =
        new RoleDto(
            UUID.randomUUID(), "ADMIN", Set.of(new PermissaoDto(UUID.randomUUID(), "USER_READ")));
    when(roleService.adicionarPermissaoNaRole("ADMIN", "USER_READ")).thenReturn(roleDto);

    mockMvc
        .perform(
            post("/api/v1/admin/roles/ADMIN/permissoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "permissao":"USER_READ"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nome").value("ADMIN"))
        .andExpect(jsonPath("$.permissoes[0].nome").value("USER_READ"));
  }

  @Test
  @DisplayName("Deve atribuir role ao usuario")
  void atribuirRoleDeveRetornarOk() throws Exception {
    UUID usuarioId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UsuarioComRolesDto usuario =
        new UsuarioComRolesDto(
            usuarioId,
            "Ana",
            "ana@email.com",
            Set.of(new RoleResumoDto(UUID.randomUUID(), "ADMIN")));
    when(roleService.atribuirRoleAoUsuario(usuarioId, "ADMIN")).thenReturn(usuario);

    mockMvc
        .perform(post("/api/v1/admin/usuarios/{usuarioId}/roles/{roleNome}", usuarioId, "ADMIN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("ana@email.com"))
        .andExpect(jsonPath("$.roles[0].nome").value("ADMIN"));
  }
}
