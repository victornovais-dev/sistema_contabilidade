package com.sistema_contabilidade.usuario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioSelfUpdateRequest(
    @NotBlank(message = "Nome e obrigatorio")
        @Size(max = 120, message = "Nome deve ter no maximo 120 caracteres")
        String nome,
    @NotBlank(message = "Email e obrigatorio") @Email(message = "Email deve ser valido")
        String email,
    @Size(min = 6, message = "Senha deve ter pelo menos 6 caracteres")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String senha) {}
