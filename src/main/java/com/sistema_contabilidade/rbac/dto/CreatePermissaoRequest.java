package com.sistema_contabilidade.rbac.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePermissaoRequest(
    @NotBlank(message = "Nome da permissao e obrigatorio") String nome) {}
