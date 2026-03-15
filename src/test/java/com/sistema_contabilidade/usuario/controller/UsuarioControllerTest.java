package com.sistema_contabilidade.usuario.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.usuario.dto.UsuarioCreateRequest;
import com.sistema_contabilidade.usuario.dto.UsuarioResponse;
import com.sistema_contabilidade.usuario.dto.UsuarioUpdateRequest;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.service.UsuarioService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsuarioController unit tests")
class UsuarioControllerTest {

  @Mock private UsuarioService usuarioService;

  @InjectMocks private UsuarioController usuarioController;

  @Test
  @DisplayName("Deve criar usuario delegando para o service")
  void criarDeveDelegarParaService() {
    // Arrange
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UsuarioCreateRequest request = new UsuarioCreateRequest("Ana", "ana@email.com", "123456");
    Usuario usuario = novoUsuario(id, "Ana", "ana@email.com");
    when(usuarioService.criar(any(Usuario.class))).thenReturn(usuario);
    MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    servletRequest.setRequestURI("/api/v1/usuarios");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));

    // Act
    ResponseEntity<UsuarioResponse> resultado = usuarioController.criar(request);

    // Assert
    assertEquals(HttpStatus.CREATED, resultado.getStatusCode());
    assertEquals(id, resultado.getBody().id());
    ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
    verify(usuarioService).criar(captor.capture());
    assertEquals("Ana", captor.getValue().getNome());
    assertEquals("ana@email.com", captor.getValue().getEmail());
    assertEquals("123456", captor.getValue().getSenha());
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  @DisplayName("Deve listar usuarios delegando para o service")
  void listarTodosDeveDelegarParaService() {
    // Arrange
    List<Usuario> usuarios =
        List.of(
            novoUsuario(
                UUID.fromString("11111111-1111-1111-1111-111111111111"), "Ana", "ana@email.com"),
            novoUsuario(
                UUID.fromString("22222222-2222-2222-2222-222222222222"), "Bia", "bia@email.com"));
    when(usuarioService.listarTodos()).thenReturn(usuarios);

    // Act
    ResponseEntity<List<UsuarioResponse>> resultado = usuarioController.listarTodos();

    // Assert
    assertEquals(HttpStatus.OK, resultado.getStatusCode());
    assertEquals(2, resultado.getBody().size());
    verify(usuarioService).listarTodos();
  }

  @Test
  @DisplayName("Deve buscar usuario por id delegando para o service")
  void buscarPorIdDeveDelegarParaService() {
    // Arrange
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    Usuario usuario = novoUsuario(id, "Ana", "ana@email.com");
    when(usuarioService.buscarPorId(id)).thenReturn(usuario);

    // Act
    ResponseEntity<UsuarioResponse> resultado = usuarioController.buscarPorId(id);

    // Assert
    assertEquals(HttpStatus.OK, resultado.getStatusCode());
    assertEquals(usuario.getId(), resultado.getBody().id());
    verify(usuarioService).buscarPorId(id);
  }

  @Test
  @DisplayName("Deve atualizar usuario delegando para o service")
  void atualizarDeveDelegarParaService() {
    // Arrange
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UsuarioUpdateRequest request = new UsuarioUpdateRequest("Ana Maria", "ana.maria@email.com");
    Usuario atualizado = novoUsuario(id, "Ana Maria", "ana.maria@email.com");
    when(usuarioService.atualizar(eq(id), any(Usuario.class))).thenReturn(atualizado);

    // Act
    ResponseEntity<UsuarioResponse> resultado = usuarioController.atualizar(id, request);

    // Assert
    assertEquals(HttpStatus.OK, resultado.getStatusCode());
    assertEquals(atualizado.getId(), resultado.getBody().id());
    ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
    verify(usuarioService).atualizar(eq(id), captor.capture());
    assertEquals("Ana Maria", captor.getValue().getNome());
    assertEquals("ana.maria@email.com", captor.getValue().getEmail());
  }

  @Test
  @DisplayName("Deve deletar usuario delegando para o service")
  void deletarDeveDelegarParaService() {
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");

    // Act
    ResponseEntity<Void> resultado = usuarioController.deletar(id);

    // Assert
    assertEquals(HttpStatus.NO_CONTENT, resultado.getStatusCode());
    verify(usuarioService).deletar(id);
  }

  private Usuario novoUsuario(UUID id, String nome, String email) {
    Usuario usuario = new Usuario();
    usuario.setId(id);
    usuario.setNome(nome);
    usuario.setEmail(email);
    usuario.setSenha("123456");
    return usuario;
  }
}
