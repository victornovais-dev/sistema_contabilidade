package com.sistema_contabilidade.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UsuarioUpdateRequest(
    @NotBlank(message = "Nome e obrigatorio") String nome,
    @NotBlank(message = "Email e obrigatorio") @Email(message = "Email deve ser valido")
        String email) {}
