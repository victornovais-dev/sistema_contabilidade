package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ItemListResponse(
    UUID id,
    BigDecimal valor,
    LocalDate data,
    LocalDateTime horarioCriacao,
    String caminhoArquivoPdf,
    TipoItem tipo,
    String role,
    String descricao,
    String razaoSocialNome,
    String cnpjCpf,
    String observacao,
    boolean temArquivos) {}
