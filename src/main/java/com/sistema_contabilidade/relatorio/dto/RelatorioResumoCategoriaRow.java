package com.sistema_contabilidade.relatorio.dto;

import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;

public record RelatorioResumoCategoriaRow(TipoItem tipo, BigDecimal total, String descricao) {}
