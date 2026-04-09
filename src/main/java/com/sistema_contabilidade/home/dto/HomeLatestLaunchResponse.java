package com.sistema_contabilidade.home.dto;

import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record HomeLatestLaunchResponse(
    LocalDateTime horarioCriacao,
    LocalDate data,
    BigDecimal valor,
    TipoItem tipo,
    String descricao,
    String razaoSocialNome) {}
