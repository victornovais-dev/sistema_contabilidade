package com.sistema_contabilidade.privacidade.dto;

import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeStatus;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeTipo;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SolicitacaoPrivacidadeConsultaResponse(
    String protocolo,
    SolicitacaoPrivacidadeTipo tipo,
    SolicitacaoPrivacidadeStatus status,
    LocalDate recebidaEm,
    LocalDateTime atualizadaEm) {}
