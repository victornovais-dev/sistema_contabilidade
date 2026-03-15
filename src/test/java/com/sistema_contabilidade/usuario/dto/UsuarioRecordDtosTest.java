package com.sistema_contabilidade.usuario.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sistema_contabilidade.usuario.model.Usuario;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Usuario record DTOs unit tests")
class UsuarioRecordDtosTest {

  @Test
  @DisplayName("Deve expor campos de UsuarioCreateRequest")
  void deveExporCamposDeUsuarioCreateRequest() {
    UsuarioCreateRequest request = new UsuarioCreateRequest("Ana", "ana@email.com", "123456");

    assertEquals("Ana", request.nome());
    assertEquals("ana@email.com", request.email());
    assertEquals("123456", request.senha());
  }

  @Test
  @DisplayName("Deve expor campos de UsuarioUpdateRequest")
  void deveExporCamposDeUsuarioUpdateRequest() {
    UsuarioUpdateRequest request = new UsuarioUpdateRequest("Ana", "ana@email.com");

    assertEquals("Ana", request.nome());
    assertEquals("ana@email.com", request.email());
  }

  @Test
  @DisplayName("Deve mapear Usuario para UsuarioResponse")
  void deveMapearUsuarioParaUsuarioResponse() {
    Usuario usuario = new Usuario();
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    usuario.setId(id);
    usuario.setNome("Ana");
    usuario.setEmail("ana@email.com");

    UsuarioResponse response = UsuarioResponse.from(usuario);

    assertEquals(id, response.id());
    assertEquals("Ana", response.nome());
    assertEquals("ana@email.com", response.email());
  }
}
