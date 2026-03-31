package com.sistema_contabilidade.item.dto;

import jakarta.validation.constraints.Size;

public record ItemObservacaoUpdateRequest(@Size(max = 500) String observacao) {}
