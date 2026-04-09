package com.sistema_contabilidade.home.dto;

import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;

public record HomeMonthlyBalanceRow(int year, int month, TipoItem tipo, BigDecimal total) {}
