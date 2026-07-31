package com.sistema_contabilidade.privacidade.dto;

public record SolicitacaoPrivacidadeResumoResponse(
    long abertas, long prazoProximo, long atrasadas, long concluidas) {}
