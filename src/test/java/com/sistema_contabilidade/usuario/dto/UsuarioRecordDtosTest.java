package com.sistema_contabilidade.usuario.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.usuario.model.Usuario;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Usuario record DTOs unit tests")
class UsuarioRecordDtosTest {

  @Test
  @DisplayName("Deve expor campos de UsuarioCreateRequest")
  void deveExporCamposDeUsuarioCreateRequest() {
    UsuarioCreateRequest request =
        new UsuarioCreateRequest("Ana", "ana@email.com", "123456", "ADMIN", Set.of("SUPPORT"));

    assertEquals("Ana", request.nome());
    assertEquals("ana@email.com", request.email());
    assertEquals("123456", request.senha());
    assertEquals("ADMIN", request.role());
    assertEquals(Set.of("SUPPORT"), request.roles());
    assertFalse(request.deveForcarTrocaSenha());
    assertTrue(request.possuiRolesValidas());
  }

  @Test
  @DisplayName("Deve identificar criacao com troca de senha obrigatoria")
  void deveIdentificarCriacaoComTrocaDeSenhaObrigatoria() {
    UsuarioCreateRequest request =
        new UsuarioCreateRequest("Ana", "ana@email.com", "123456", "ADMIN", Set.of("ADMIN"), true);

    assertTrue(request.deveForcarTrocaSenha());
  }

  @Test
  @DisplayName("Deve expor campos de UsuarioUpdateRequest")
  void deveExporCamposDeUsuarioUpdateRequest() {
    UsuarioUpdateRequest request = new UsuarioUpdateRequest("Ana", "ana@email.com");

    assertEquals("Ana", request.nome());
    assertEquals("ana@email.com", request.email());
  }

  @Test
  @DisplayName("Deve validar roles informadas na atualizacao por email")
  void deveValidarRolesInformadasNaAtualizacaoPorEmail() {
    UsuarioUpdateByEmailRequest valida =
        new UsuarioUpdateByEmailRequest("ana@email.com", null, null, Set.of("ADMIN"), false);
    UsuarioUpdateByEmailRequest invalida =
        new UsuarioUpdateByEmailRequest("ana@email.com", null, null, Set.of(" "), false);
    UsuarioUpdateByEmailRequest comNovoEmail =
        new UsuarioUpdateByEmailRequest(
            "ana@email.com", null, null, Set.of("ADMIN"), false, "ana.nova@email.com");

    assertTrue(valida.possuiRolesValidas());
    assertFalse(invalida.possuiRolesValidas());
    assertEquals("ana.nova@email.com", comNovoEmail.novoEmail());
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
