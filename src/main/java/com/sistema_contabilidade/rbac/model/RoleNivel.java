package com.sistema_contabilidade.rbac.model;

import java.util.Arrays;

public enum RoleNivel {
  ADMIN,
  MANAGER,
  OPERATOR,
  SUPPORT,
  CUSTOMER;

  public static RoleNivel fromNome(String nome) {
    if (nome == null || nome.isBlank()) {
      throw new IllegalArgumentException("Role invalida");
    }
    return Arrays.stream(values())
        .filter(roleNivel -> roleNivel.name().equalsIgnoreCase(nome.trim()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Role invalida: " + nome));
  }
}
