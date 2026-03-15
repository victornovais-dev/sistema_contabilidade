package com.sistema_contabilidade.usuario.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.usuario.dto.UsuarioDto;
import com.sistema_contabilidade.usuario.service.UsuarioService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    UsuarioDto request = new UsuarioDto(null, "Ana", "ana@email.com", "123456");
    UsuarioDto response = new UsuarioDto(id, "Ana", "ana@email.com", null);
    when(usuarioService.save(request)).thenReturn(response);
    MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    servletRequest.setRequestURI("/api/v1/usuarios");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));

    // Act
    ResponseEntity<UsuarioDto> resultado = usuarioController.criar(request);

    // Assert
    assertEquals(HttpStatus.CREATED, resultado.getStatusCode());
    assertEquals(id, resultado.getBody().getId());
    verify(usuarioService).save(request);
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  @DisplayName("Deve listar usuarios delegando para o service")
  void listarTodosDeveDelegarParaService() {
    // Arrange
    List<UsuarioDto> usuarios =
        List.of(
            new UsuarioDto(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Ana",
                "ana@email.com",
                null),
            new UsuarioDto(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Bia",
                "bia@email.com",
                null));
    when(usuarioService.listarTodos()).thenReturn(usuarios);

    // Act
    ResponseEntity<List<UsuarioDto>> resultado = usuarioController.listarTodos();

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
    UsuarioDto usuario = new UsuarioDto(id, "Ana", "ana@email.com", null);
    when(usuarioService.findById(id)).thenReturn(usuario);

    // Act
    ResponseEntity<UsuarioDto> resultado = usuarioController.buscarPorId(id);

    // Assert
    assertEquals(HttpStatus.OK, resultado.getStatusCode());
    assertEquals(usuario.getId(), resultado.getBody().getId());
    verify(usuarioService).findById(id);
  }

  @Test
  @DisplayName("Deve atualizar usuario delegando para o service")
  void atualizarDeveDelegarParaService() {
    // Arrange
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UsuarioDto request = new UsuarioDto(null, "Ana Maria", "ana.maria@email.com", null);
    UsuarioDto atualizado = new UsuarioDto(id, "Ana Maria", "ana.maria@email.com", null);
    when(usuarioService.update(id, request)).thenReturn(atualizado);

    // Act
    ResponseEntity<UsuarioDto> resultado = usuarioController.atualizar(id, request);

    // Assert
    assertEquals(HttpStatus.OK, resultado.getStatusCode());
    assertEquals(atualizado.getId(), resultado.getBody().getId());
    verify(usuarioService).update(id, request);
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
}
