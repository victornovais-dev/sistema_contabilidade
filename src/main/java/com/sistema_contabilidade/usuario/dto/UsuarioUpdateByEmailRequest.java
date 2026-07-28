package com.sistema_contabilidade.usuario.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UsuarioUpdateByEmailRequest(
    @NotBlank(message = "Email e obrigatorio") @Email(message = "Email deve ser valido")
        String email,
    @Size(min = 6, message = "Senha deve ter pelo menos 6 caracteres") String senha,
    @NotEmpty(message = "Ao menos uma role deve ser informada") Set<String> roles) {

  public UsuarioUpdateByEmailRequest {
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
