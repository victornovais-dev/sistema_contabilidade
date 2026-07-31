package com.sistema_contabilidade.privacidade.dto;

import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeCanal;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeEscopo;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeTipo;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeVinculo;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record SolicitacaoPrivacidadeCreateRequest(
    @NotBlank @Size(max = 120) String nome,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(max = 120) String organizacao,
    @NotNull SolicitacaoPrivacidadeVinculo vinculo,
    @NotNull SolicitacaoPrivacidadeTipo tipo,
    @NotEmpty @Size(max = 6) Set<@NotNull SolicitacaoPrivacidadeEscopo> escopos,
    @NotNull SolicitacaoPrivacidadeCanal canalResposta,
    @Size(max = 100) String referencia,
    @NotBlank @Size(min = 20, max = 1600) String descricao,
    @AssertTrue boolean avisoAceito,
    @Size(max = 200) String website) {

  public SolicitacaoPrivacidadeCreateRequest {
    escopos = escopos == null ? Set.of() : Set.copyOf(escopos);
  }
}
