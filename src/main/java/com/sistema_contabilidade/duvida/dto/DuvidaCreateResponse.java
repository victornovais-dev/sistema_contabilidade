package com.sistema_contabilidade.duvida.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DuvidaCreateResponse(UUID protocolo, LocalDateTime recebidaEm) {}
