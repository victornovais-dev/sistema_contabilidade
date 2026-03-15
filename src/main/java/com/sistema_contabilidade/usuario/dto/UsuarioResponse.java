package com.sistema_contabilidade.usuario.dto;

import com.sistema_contabilidade.usuario.model.Usuario;
import java.util.UUID;

public record UsuarioResponse(UUID id, String nome, String email) {
  public static UsuarioResponse from(Usuario usuario) {
    return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail());
  }
}
