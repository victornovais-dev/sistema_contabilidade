package com.sistema_contabilidade.rbac.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Role e Permissao model tests")
class RolePermissaoTest {

  @Test
  @DisplayName("Deve permitir setar e recuperar campos de Role")
  void devePermitirSetarERecuperarCamposDeRole() {
    Role role = new Role();
    UUID id = UUID.fromString("44444444-4444-4444-4444-444444444444");
    role.setId(id);
    role.setNome("ADMIN");

    assertEquals(id, role.getId());
    assertEquals("ADMIN", role.getNome());
    assertNotNull(role.getPermissoes());
  }

  @Test
  @DisplayName("Deve permitir setar e recuperar campos de Permissao")
  void devePermitirSetarERecuperarCamposDePermissao() {
    Permissao permissao = new Permissao();
    UUID id = UUID.fromString("55555555-5555-5555-5555-555555555555");
    permissao.setId(id);
    permissao.setNome("PERMISSAO_TESTE");

    assertEquals(id, permissao.getId());
    assertEquals("PERMISSAO_TESTE", permissao.getNome());
  }
}
