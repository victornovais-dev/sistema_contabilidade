package com.sistema_contabilidade.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record CompleteNewPasswordRequest(
    @NotBlank(message = "Nova senha e obrigatoria") String novaSenha) {}
