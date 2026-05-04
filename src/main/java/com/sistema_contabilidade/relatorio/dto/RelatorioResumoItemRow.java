package com.sistema_contabilidade.relatorio.dto;

import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;

public record RelatorioResumoItemRow(TipoItem tipo, BigDecimal valor, String descricao) {}
