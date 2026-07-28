package com.sistema_contabilidade.usuario.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;

public record EstagiarioPermissoesUpdateRequest(
    @NotBlank(message = "Email e obrigatorio") @Email(message = "Email deve ser valido")
        String email,
    Set<String> roles) {

  public EstagiarioPermissoesUpdateRequest {
    roles = roles == null ? Set.of() : Set.copyOf(roles);
  }

  @Override
  public Set<String> roles() {
    return Set.copyOf(roles);
  }

  @AssertTrue(message = "Role nao pode ser vazia ou ter mais de 80 caracteres")
  public boolean possuiRolesValidas() {
    return roles.stream().allMatch(role -> role != null && !role.isBlank() && role.length() <= 80);
  }
}
