package com.sistema_contabilidade.privacidade.dto;

import java.time.LocalDate;

public record SolicitacaoPrivacidadeCreateResponse(
    String protocolo, LocalDate recebidaEm, LocalDate prazo) {}
