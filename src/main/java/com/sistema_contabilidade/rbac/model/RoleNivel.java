package com.sistema_contabilidade.rbac.model;

import java.util.Arrays;
import java.util.Locale;

public enum RoleNivel {
  ADMIN("ADMIN"),
  MANAGER("MANAGER"),
  TARCISIO("TARCISIO"),
  KIM_KATAGUIRI("KIM KATAGUIRI"),
  VALDEMAR("VALDEMAR");

  private final String nomeBanco;

  RoleNivel(String nomeBanco) {
    this.nomeBanco = nomeBanco;
  }

  public static RoleNivel fromNome(String nome) {
    if (nome == null || nome.isBlank()) {
      throw new IllegalArgumentException("Role invalida");
    }
    String nomeNormalizado = normalizar(nome);
    return Arrays.stream(values())
        .filter(roleNivel -> normalizar(roleNivel.nomeBanco).equals(nomeNormalizado))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Role invalida: " + nome));
  }

  public String valorBanco() {
    return nomeBanco;
  }

  private static String normalizar(String valor) {
    return valor == null
        ? ""
        : valor.trim().replace('_', ' ').replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
  }
}
