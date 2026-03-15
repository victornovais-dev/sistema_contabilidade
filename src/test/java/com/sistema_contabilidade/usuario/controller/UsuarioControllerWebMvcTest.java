package com.sistema_contabilidade.usuario.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import com.sistema_contabilidade.usuario.dto.UsuarioDto;
import com.sistema_contabilidade.usuario.service.UsuarioService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UsuarioController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("UsuarioController WebMvc tests")
class UsuarioControllerWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UsuarioService usuarioService;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private CustomUserDetailsService customUserDetailsService;

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
        .andExpect(jsonPath("$[0].email").value("ana@email.com"));
  }

  @Test
  @DisplayName("Deve criar usuario")
  void criarDeveRetornarCreated() throws Exception {
    UsuarioDto response = new UsuarioDto(UUID.randomUUID(), "Bia", "bia@email.com", null);
    when(usuarioService.save(org.mockito.ArgumentMatchers.any(UsuarioDto.class)))
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
                      "senha":"123456"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.nome").value("Bia"))
        .andExpect(jsonPath("$.email").value("bia@email.com"));
  }
}
