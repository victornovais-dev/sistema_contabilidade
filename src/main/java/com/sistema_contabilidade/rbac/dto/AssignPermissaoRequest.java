package com.sistema_contabilidade.rbac.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignPermissaoRequest(
    @NotBlank(message = "Nome da permissao e obrigatorio") String permissao) {}
