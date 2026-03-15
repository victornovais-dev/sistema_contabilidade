package com.sistema_contabilidade.auth.dto;

import java.util.UUID;

public record LoginResponse(String message, UUID usuarioId) {}
