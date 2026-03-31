package com.sistema_contabilidade.auth.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SessaoUsuario model tests")
class SessaoUsuarioTest {

  @Test
  @DisplayName("Deve permitir setar e recuperar campos de SessaoUsuario")
  void devePermitirSetarERecuperarCamposDeSessaoUsuario() {
    SessaoUsuario sessao = new SessaoUsuario();
    UUID id = UUID.fromString("77777777-7777-7777-7777-777777777777");
    UUID usuarioId = UUID.fromString("88888888-8888-8888-8888-888888888888");
    LocalDateTime criada = LocalDateTime.of(2026, 3, 29, 10, 0);
    LocalDateTime expira = criada.plusHours(8);

    sessao.setId(id);
    sessao.setUsuarioId(usuarioId);
    sessao.setCriadaEm(criada);
    sessao.setExpiraEm(expira);
    sessao.setRevogada(true);

    assertEquals(id, sessao.getId());
    assertEquals(usuarioId, sessao.getUsuarioId());
    assertEquals(criada, sessao.getCriadaEm());
    assertEquals(expira, sessao.getExpiraEm());
    assertEquals(true, sessao.isRevogada());
  }
}
