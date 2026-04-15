package com.sistema_contabilidade.notificacao.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record NotificacaoListResponse(
    UUID id,
    UUID itemId,
    String role,
    String descricao,
    String razaoSocialNome,
    BigDecimal valor,
    LocalDateTime criadoEm) {}
