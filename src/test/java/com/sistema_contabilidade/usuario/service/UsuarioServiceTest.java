package com.sistema_contabilidade.usuario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsuarioService unit tests")
class UsuarioServiceTest {

  @Mock private UsuarioRepository usuarioRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private UsuarioService usuarioService;

  @Test
  @DisplayName("Deve criar usuario com sucesso")
  void criarDeveSalvarUsuario() {
    // Arrange
    Usuario usuario =
        novoUsuario(
            UUID.fromString("11111111-1111-1111-1111-111111111111"), "Ana", "ana@email.com");
    when(passwordEncoder.encode("123456")).thenReturn("encoded-123456");
    when(usuarioRepository.save(usuario)).thenReturn(usuario);

    // Act
    Usuario resultado = usuarioService.criar(usuario);

    // Assert
    assertEquals(usuario, resultado);
    verify(usuarioRepository).save(usuario);
  }

  @Test
  @DisplayName("Deve listar todos os usuarios")
  void listarTodosDeveRetornarLista() {
    // Arrange
    List<Usuario> usuarios =
        List.of(
            novoUsuario(
                UUID.fromString("11111111-1111-1111-1111-111111111111"), "Ana", "ana@email.com"),
            novoUsuario(
                UUID.fromString("22222222-2222-2222-2222-222222222222"), "Bia", "bia@email.com"));
    when(usuarioRepository.findAll()).thenReturn(usuarios);

    // Act
    List<Usuario> resultado = usuarioService.listarTodos();

    // Assert
    assertEquals(2, resultado.size());
    verify(usuarioRepository).findAll();
  }

  @Test
  @DisplayName("Deve buscar usuario por id quando existe")
  void buscarPorIdQuandoExisteDeveRetornarUsuario() {
    // Arrange
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    Usuario usuario = novoUsuario(id, "Ana", "ana@email.com");
    when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuario));

    // Act
    Usuario resultado = usuarioService.buscarPorId(id);

    // Assert
    assertEquals(usuario, resultado);
    verify(usuarioRepository).findById(id);
  }

  @Test
  @DisplayName("Deve retornar 404 ao buscar usuario inexistente")
  void buscarPorIdQuandoNaoExisteDeveLancarNotFound() {
    // Arrange
    UUID id = UUID.fromString("99999999-9999-9999-9999-999999999999");
    when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

    // Act
    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> usuarioService.buscarPorId(id));

    // Assert
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    assertTrue(ex.getReason().contains("Usuario nao encontrado"));
  }

  @Test
  @DisplayName("Deve atualizar usuario quando ele existe")
  void atualizarQuandoExisteDeveAtualizarCampos() {
    // Arrange
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    Usuario existente = novoUsuario(id, "Ana", "ana@email.com");
    Usuario atualizacao = novoUsuario(null, "Ana Maria", "ana.maria@email.com");
    Usuario salvo = novoUsuario(id, "Ana Maria", "ana.maria@email.com");
    when(usuarioRepository.findById(id)).thenReturn(Optional.of(existente));
    when(passwordEncoder.encode("654321")).thenReturn("encoded-654321");
    when(usuarioRepository.save(existente)).thenReturn(salvo);

    // Act
    Usuario resultado = usuarioService.atualizar(id, atualizacao);

    // Assert
    assertEquals("Ana Maria", resultado.getNome());
    assertEquals("ana.maria@email.com", resultado.getEmail());
    verify(usuarioRepository).findById(id);
    verify(usuarioRepository).save(existente);
  }

  @Test
  @DisplayName("Deve retornar 404 ao atualizar usuario inexistente")
  void atualizarQuandoNaoExisteDeveLancarNotFound() {
    // Arrange
    Usuario atualizacao = novoUsuario(null, "Ana Maria", "ana.maria@email.com");
    UUID id = UUID.fromString("99999999-9999-9999-9999-999999999999");
    when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

    // Act / Assert
    assertThrows(ResponseStatusException.class, () -> usuarioService.atualizar(id, atualizacao));
    verify(usuarioRepository).findById(id);
    verify(usuarioRepository, never()).save(any());
  }

  @Test
  @DisplayName("Deve deletar usuario quando ele existe")
  void deletarQuandoExisteDeveRemoverUsuario() {
    // Arrange
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    Usuario usuario = novoUsuario(id, "Ana", "ana@email.com");
    when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuario));

    // Act
    usuarioService.deletar(id);

    // Assert
    verify(usuarioRepository).findById(id);
    verify(usuarioRepository).delete(usuario);
  }

  @Test
  @DisplayName("Deve retornar 404 ao deletar usuario inexistente")
  void deletarQuandoNaoExisteDeveLancarNotFound() {
    // Arrange
    UUID id = UUID.fromString("99999999-9999-9999-9999-999999999999");
    when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

    // Act / Assert
    assertThrows(ResponseStatusException.class, () -> usuarioService.deletar(id));
    verify(usuarioRepository).findById(id);
    verify(usuarioRepository, never()).delete(any(Usuario.class));
  }

  private Usuario novoUsuario(UUID id, String nome, String email) {
    Usuario usuario = new Usuario();
    usuario.setId(id);
    usuario.setNome(nome);
    usuario.setEmail(email);
    usuario.setSenha(id == null ? "654321" : "123456");
    return usuario;
  }
}
