package com.sistema_contabilidade.home.dto;

import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;

public record HomeTypeTotalRow(TipoItem tipo, BigDecimal total) {}
