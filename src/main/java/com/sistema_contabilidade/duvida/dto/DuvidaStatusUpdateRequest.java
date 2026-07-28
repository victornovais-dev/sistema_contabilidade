package com.sistema_contabilidade.duvida.dto;

import com.sistema_contabilidade.duvida.model.DuvidaStatus;
import jakarta.validation.constraints.NotNull;

public record DuvidaStatusUpdateRequest(@NotNull DuvidaStatus status) {}
