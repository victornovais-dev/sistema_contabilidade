package com.sistema_contabilidade.auth.dto;

import java.util.UUID;

public record LoginSessionResult(UUID usuarioId, String token) {}
