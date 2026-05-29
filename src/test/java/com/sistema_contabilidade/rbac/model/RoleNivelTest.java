package com.sistema_contabilidade.rbac.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RoleNivel unit tests")
class RoleNivelTest {

  @Test
  @DisplayName("Deve resolver role ignorando caixa e espacos laterais")
  void deveResolverRoleIgnorandoCaixaEEspacosLaterais() {
    assertEquals(RoleNivel.MANAGER, RoleNivel.fromNome("  manager  "));
    assertEquals(RoleNivel.CONTABIL, RoleNivel.fromNome("contabil"));
  }

  @Test
  @DisplayName("Deve expor valor persistido no banco")
  void deveExporValorPersistidoNoBanco() {
    assertEquals("SUPPORT", RoleNivel.SUPPORT.valorBanco());
  }

  @Test
  @DisplayName("Deve rejeitar role vazia ou desconhecida")
  void deveRejeitarRoleVaziaOuDesconhecida() {
    assertThrows(IllegalArgumentException.class, () -> RoleNivel.fromNome(" "));
    assertThrows(IllegalArgumentException.class, () -> RoleNivel.fromNome("desconhecida"));
  }
}
