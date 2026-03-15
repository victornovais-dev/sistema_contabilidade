package com.sistema_contabilidade.rbac.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRoleRequest(@NotBlank(message = "Nome da role e obrigatorio") String nome) {}
