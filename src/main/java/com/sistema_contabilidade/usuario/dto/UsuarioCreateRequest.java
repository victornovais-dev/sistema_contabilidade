package com.sistema_contabilidade.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioCreateRequest(
    @NotBlank(message = "Nome e obrigatorio") String nome,
    @NotBlank(message = "Email e obrigatorio") @Email(message = "Email deve ser valido")
        String email,
    @NotBlank(message = "Senha e obrigatoria")
        @Size(min = 6, message = "Senha deve ter pelo menos 6 caracteres")
        String senha) {}
