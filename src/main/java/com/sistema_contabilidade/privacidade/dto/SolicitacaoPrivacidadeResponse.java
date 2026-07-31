package com.sistema_contabilidade.privacidade.dto;

import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeCanal;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeEscopo;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeStatus;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeTipo;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeVinculo;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record SolicitacaoPrivacidadeResponse(
    String protocolo,
    String nome,
    String email,
    String organizacao,
    SolicitacaoPrivacidadeVinculo vinculo,
    SolicitacaoPrivacidadeTipo tipo,
    Set<SolicitacaoPrivacidadeEscopo> escopos,
    SolicitacaoPrivacidadeCanal canalResposta,
    String referencia,
    SolicitacaoPrivacidadeStatus status,
    LocalDate recebidaEm,
    LocalDate prazo,
    String responsavel,
    boolean identidadeVerificada,
    String descricao,
    boolean retencaoLegal,
    String motivoRetencao,
    LocalDateTime criadaEm,
    LocalDateTime atualizadaEm,
    List<SolicitacaoPrivacidadeEventoResponse> eventos) {

  public SolicitacaoPrivacidadeResponse {
    escopos = Set.copyOf(escopos);
    eventos = List.copyOf(eventos);
  }
}
