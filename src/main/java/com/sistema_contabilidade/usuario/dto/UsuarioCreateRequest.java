package com.sistema_contabilidade.usuario.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UsuarioCreateRequest(
    @NotBlank(message = "Nome e obrigatorio")
        @Size(max = 120, message = "Nome deve ter no maximo 120 caracteres")
        String nome,
    @NotBlank(message = "Email e obrigatorio") @Email(message = "Email deve ser valido")
        String email,
    @NotBlank(message = "Senha e obrigatoria")
    @Size(min = 6, message = "Senha deve ter pelo menos 6 caracteres")
        String senha,
    @Size(max = 80, message = "Role deve ter no maximo 80 caracteres") String role,
    Set<String> roles,
    Boolean forcarTrocaSenha) {

  public UsuarioCreateRequest {
    roles = roles == null ? Set.of() : Set.copyOf(roles);
  }

  public UsuarioCreateRequest(String nome, String email, String senha, String role, Set<String> roles) {
    this(nome, email, senha, role, roles, null);
  }

  @Override
  public Set<String> roles() {
    return Set.copyOf(roles);
  }

  @AssertTrue(message = "Role nao pode ser vazia ou ter mais de 80 caracteres")
  public boolean possuiRolesValidas() {
    return roles.stream().allMatch(role -> role != null && !role.isBlank() && role.length() <= 80);
  }

  public boolean deveForcarTrocaSenha() {
    return Boolean.TRUE.equals(forcarTrocaSenha);
  }
}
