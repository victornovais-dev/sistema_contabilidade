package com.sistema_contabilidade.privacidade.dto;

import java.time.LocalDateTime;

public record SolicitacaoPrivacidadeEventoResponse(
    String titulo, String descricao, String ator, LocalDateTime ocorridoEm) {}
