package com.sistema_contabilidade.privacidade.dto;

import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeStatus;
import jakarta.validation.constraints.Size;

public record SolicitacaoPrivacidadeUpdateRequest(
    SolicitacaoPrivacidadeStatus status,
    Boolean identidadeVerificada,
    Boolean retencaoLegal,
    @Size(max = 1000) String motivoRetencao,
    @Size(max = 100) String responsavel,
    @Size(max = 1000) String observacao) {}
