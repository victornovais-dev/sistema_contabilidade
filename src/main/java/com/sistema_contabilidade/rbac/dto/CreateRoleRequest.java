package com.sistema_contabilidade.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
    @NotBlank(message = "Nome da role e obrigatorio")
        @Size(max = 80, message = "Nome da role deve ter no maximo 80 caracteres")
        String nome) {}
