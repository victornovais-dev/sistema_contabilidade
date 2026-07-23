package com.sistema_contabilidade.relatorio.dto;

import com.sistema_contabilidade.item.model.ContaOrigemPagamentoItem;
import java.math.BigDecimal;

public record RelatorioContaPagamentoRow(
    ContaOrigemPagamentoItem contaOrigemPagamento, BigDecimal totalPago) {}
