package com.sistema_contabilidade.auth.dto;

import java.util.UUID;

public record SessionValidationResponse(boolean valid, UUID usuarioId) {}
