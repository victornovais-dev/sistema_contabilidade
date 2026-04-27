package com.sistema_contabilidade.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssignPermissaoRequest(
    @NotBlank(message = "Nome da permissao e obrigatorio")
        @Size(max = 80, message = "Nome da permissao deve ter no maximo 80 caracteres")
        String permissao) {}
