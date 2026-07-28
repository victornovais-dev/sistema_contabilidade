package com.sistema_contabilidade.duvida.dto;

import com.sistema_contabilidade.duvida.model.DuvidaStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record DuvidaResponse(
    UUID protocolo,
    String nome,
    String email,
    String duvida,
    LocalDateTime recebidaEm,
    DuvidaStatus status) {}
