package com.sistema_contabilidade.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioDto {

  private UUID id;

  @NotBlank(message = "Nome e obrigatorio")
  private String nome;

  @NotBlank(message = "Email e obrigatorio")
  @Email(message = "Email deve ser valido")
  private String email;

  @Size(min = 6, message = "Senha deve ter pelo menos 6 caracteres")
  private String senha;
}
