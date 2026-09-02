package com.sistema_contabilidade.item.dto;

import jakarta.validation.constraints.Size;

public record ItemObservacaoUpdateRequest(@Size(max = 2000) String observacao) {}
