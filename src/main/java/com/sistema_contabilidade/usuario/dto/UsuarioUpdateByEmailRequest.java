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
    @Size(max = 120, message = "Nome deve ter no maximo 120 caracteres") String nome,
    @Size(min = 6, message = "Senha deve ter pelo menos 6 caracteres") String senha,
    @NotEmpty(message = "Ao menos uma role deve ser informada") Set<String> roles,
    Boolean forcarTrocaSenha) {

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

  @AssertTrue(message = "Informe uma senha temporaria para forcar a troca no proximo login")
  public boolean possuiSenhaParaTrocaObrigatoria() {
    return !deveForcarTrocaSenha() || (senha != null && !senha.isBlank());
  }

  public boolean deveForcarTrocaSenha() {
    return Boolean.TRUE.equals(forcarTrocaSenha);
  }
}
