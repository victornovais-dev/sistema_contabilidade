package com.sistema_contabilidade.privacidade.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SolicitacaoPrivacidadeConsultaRequest(
    @NotBlank @Pattern(regexp = "LGPD-\\d{4}-[A-F\\d]{12}", message = "Protocolo invalido")
        String protocolo,
    @NotBlank @Email @Size(max = 255) String email) {}
