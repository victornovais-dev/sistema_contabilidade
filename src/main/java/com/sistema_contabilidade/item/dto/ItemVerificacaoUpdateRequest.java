package com.sistema_contabilidade.item.dto;

import jakarta.validation.constraints.NotNull;

public record ItemVerificacaoUpdateRequest(@NotNull Boolean verificado) {}
