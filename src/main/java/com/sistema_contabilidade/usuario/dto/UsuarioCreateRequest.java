package com.sistema_contabilidade.usuario.dto;

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
    Set<
            @NotBlank(message = "Role nao pode ser vazia")
            @Size(max = 80, message = "Role deve ter no maximo 80 caracteres") String>
        roles) {

  public UsuarioCreateRequest {
    roles = roles == null ? Set.of() : Set.copyOf(roles);
  }

  @Override
  public Set<String> roles() {
    return Set.copyOf(roles);
  }
}
