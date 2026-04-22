package com.sistema_contabilidade.notificacao.dto;

import jakarta.validation.constraints.NotNull;

public record NotificacaoLimpezaUpdateRequest(@NotNull Boolean limpa) {}
