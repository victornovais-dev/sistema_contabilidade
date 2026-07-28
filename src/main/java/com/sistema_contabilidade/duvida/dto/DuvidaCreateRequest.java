package com.sistema_contabilidade.duvida.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DuvidaCreateRequest(
    @NotBlank(message = "Nome e obrigatorio") @Size(max = 120, message = "Nome muito longo")
        String nome,
    @NotBlank(message = "Email e obrigatorio")
        @Email(message = "Email deve ser valido")
        @Size(max = 255, message = "Email muito longo")
        String email,
    @NotBlank(message = "Duvida e obrigatoria")
        @Size(min = 10, max = 1200, message = "Duvida deve ter entre 10 e 1200 caracteres")
        String duvida) {}
