package com.sistema_contabilidade.usuario.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UsuarioDto unit tests")
class UsuarioDtoTest {

  @Test
  @DisplayName("Deve expor campos do UsuarioDto")
  void deveExporCamposDoUsuarioDto() {
    UUID id = UUID.fromString("33333333-3333-3333-3333-333333333333");
    UsuarioDto dto = new UsuarioDto(id, "Nome Teste", "teste@email.com", "senha123");

    assertEquals(id, dto.getId());
    assertEquals("Nome Teste", dto.getNome());
    assertEquals("teste@email.com", dto.getEmail());
    assertEquals("senha123", dto.getSenha());
  }
}
