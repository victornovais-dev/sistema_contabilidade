package com.sistema_contabilidade.usuario.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Usuario model tests")
class UsuarioModelTest {

  @Test
  @DisplayName("Deve permitir setar e recuperar campos de Usuario")
  void devePermitirSetarERecuperarCamposDeUsuario() {
    Usuario usuario = new Usuario();
    UUID id = UUID.fromString("66666666-6666-6666-6666-666666666666");
    usuario.setId(id);
    usuario.setNome("Usuario");
    usuario.setEmail("usuario@email.com");
    usuario.setSenha("senha123");

    assertEquals(id, usuario.getId());
    assertEquals("Usuario", usuario.getNome());
    assertEquals("usuario@email.com", usuario.getEmail());
    assertEquals("senha123", usuario.getSenha());
    assertNotNull(usuario.getRoles());
  }
}
