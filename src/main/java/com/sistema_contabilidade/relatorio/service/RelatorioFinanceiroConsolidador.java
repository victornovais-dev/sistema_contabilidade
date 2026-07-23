package com.sistema_contabilidade.relatorio.service;

import com.sistema_contabilidade.common.util.RevenueClassificationUtils;
import com.sistema_contabilidade.item.model.ContaOrigemPagamentoItem;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.relatorio.dto.RelatorioContaPagamentoRow;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResponse;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResumoResponse;
import com.sistema_contabilidade.relatorio.dto.RelatorioItemDto;
import com.sistema_contabilidade.relatorio.dto.RelatorioResumoCategoriaRow;
import com.sistema_contabilidade.relatorio.dto.RelatorioSaldoContaResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class RelatorioFinanceiroConsolidador {

  private static final BigDecimal LIMITE_COMBUSTIVEL_RATIO = new BigDecimal("0.10");
  private static final BigDecimal LIMITE_ALIMENTACAO_RATIO = new BigDecimal("0.10");
  private static final BigDecimal LIMITE_LOCACAO_RATIO = new BigDecimal("0.20");
  private static final String CONTA_DC = "CONTA DC";
  private static final String CONTA_FEFC = "CONTA FEFC";
  private static final String CONTA_FP = "CONTA FP";
  private static final Set<String> DESPESAS_ADVOCACIA_CONTABILIDADE =
      Set.of("SERVICOS ADVOCATICIOS", "SERVICOS CONTABEIS");
  private static final Set<String> DESPESAS_COMBUSTIVEL = Set.of("COMBUSTIVEIS E LUBRIFICANTES");
  private static final Set<String> DESPESAS_ALIMENTACAO = Set.of("ALIMENTACAO");
  private static final Set<String> DESPESAS_LOCACAO = Set.of("ALUGUEL DE VEICULOS");

  private final boolean includeDetailedLists;
  private final List<RelatorioItemDto> receitas;
  private final List<RelatorioItemDto> despesas;
  private BigDecimal receitasFinanceiras = BigDecimal.ZERO;
  private BigDecimal receitasContaDc = BigDecimal.ZERO;
  private BigDecimal receitasContaFefc = BigDecimal.ZERO;
  private BigDecimal receitasContaFp = BigDecimal.ZERO;
  private BigDecimal receitasEstimaveis = BigDecimal.ZERO;
  private BigDecimal totalReceitas = BigDecimal.ZERO;
  private BigDecimal despesasAdvocaciaContabilidade = BigDecimal.ZERO;
  private BigDecimal totalDespesas = BigDecimal.ZERO;
  private BigDecimal despesasCombustivel = BigDecimal.ZERO;
  private BigDecimal despesasAlimentacao = BigDecimal.ZERO;
  private BigDecimal despesasLocacaoVeiculos = BigDecimal.ZERO;

  private RelatorioFinanceiroConsolidador(boolean includeDetailedLists) {
    this.includeDetailedLists = includeDetailedLists;
    this.receitas = includeDetailedLists ? new ArrayList<>() : List.of();
    this.despesas = includeDetailedLists ? new ArrayList<>() : List.of();
  }

  static RelatorioFinanceiroResponse buildDetailedResponse(List<RelatorioItemDto> itensVisiveis) {
    RelatorioFinanceiroConsolidador consolidador = new RelatorioFinanceiroConsolidador(true);
    for (RelatorioItemDto item : itensVisiveis) {
      consolidador.accept(item);
    }
    return consolidador.toDetailedResponse();
  }

  static RelatorioFinanceiroResumoResponse buildSummaryResponse(
      List<RelatorioResumoCategoriaRow> itensVisiveis,
      BigDecimal contasPagas,
      List<RelatorioContaPagamentoRow> contasPagasPorConta) {
    RelatorioFinanceiroConsolidador consolidador = new RelatorioFinanceiroConsolidador(false);
    for (RelatorioResumoCategoriaRow item : itensVisiveis) {
      consolidador.accept(item.tipo(), item.total(), item.descricao(), null);
    }
    return consolidador.toResumoResponse(contasPagas, contasPagasPorConta);
  }

  private void accept(RelatorioItemDto item) {
    accept(item.tipo(), item.valor(), item.descricao(), item);
  }

  private void accept(TipoItem tipo, BigDecimal valor, String descricao, RelatorioItemDto item) {
    BigDecimal valorSeguro = valor == null ? BigDecimal.ZERO : valor;
    if (tipo == TipoItem.RECEITA) {
      acceptReceita(valorSeguro, descricao, item);
      return;
    }
    if (tipo == TipoItem.DESPESA) {
      acceptDespesa(valorSeguro, descricao, item);
    }
  }

  private void acceptReceita(BigDecimal valorSeguro, String descricao, RelatorioItemDto item) {
    totalReceitas = totalReceitas.add(valorSeguro);
    if (RevenueClassificationUtils.isFinancialRevenue(descricao)) {
      receitasFinanceiras = receitasFinanceiras.add(valorSeguro);
    }
    acceptReceitaPorConta(valorSeguro, descricao);
    if (RevenueClassificationUtils.isEstimatedRevenue(descricao)) {
      receitasEstimaveis = receitasEstimaveis.add(valorSeguro);
    }
    if (includeDetailedLists && item != null) {
      receitas.add(item);
    }
  }

  private void acceptDespesa(BigDecimal valorSeguro, String descricao, RelatorioItemDto item) {
    totalDespesas = totalDespesas.add(valorSeguro);
    String categoriaNormalizada = normalizeCategoryName(descricao);
    if (DESPESAS_ADVOCACIA_CONTABILIDADE.contains(categoriaNormalizada)) {
      despesasAdvocaciaContabilidade = despesasAdvocaciaContabilidade.add(valorSeguro);
    }
    if (DESPESAS_COMBUSTIVEL.contains(categoriaNormalizada)) {
      despesasCombustivel = despesasCombustivel.add(valorSeguro);
    }
    if (DESPESAS_ALIMENTACAO.contains(categoriaNormalizada)) {
      despesasAlimentacao = despesasAlimentacao.add(valorSeguro);
    }
    if (DESPESAS_LOCACAO.contains(categoriaNormalizada)) {
      despesasLocacaoVeiculos = despesasLocacaoVeiculos.add(valorSeguro);
    }
    if (includeDetailedLists && item != null) {
      despesas.add(item);
    }
  }

  private RelatorioFinanceiroResponse toDetailedResponse() {
    return new RelatorioFinanceiroResponse(
        receitasFinanceiras,
        receitasEstimaveis,
        totalReceitas,
        despesasConsideradas(),
        despesasAdvocaciaContabilidade,
        totalDespesas,
        percentualSobreDespesas(despesasCombustivel),
        percentualSobreDespesas(despesasAlimentacao),
        percentualSobreDespesas(despesasLocacaoVeiculos),
        saldoFinal(),
        receitas,
        despesas);
  }

  private void acceptReceitaPorConta(BigDecimal valor, String descricao) {
    String conta = normalizeCategoryName(descricao);
    if (CONTA_DC.equals(conta)) {
      receitasContaDc = receitasContaDc.add(valor);
      return;
    }
    if (CONTA_FEFC.equals(conta)) {
      receitasContaFefc = receitasContaFefc.add(valor);
      return;
    }
    if (CONTA_FP.equals(conta)) {
      receitasContaFp = receitasContaFp.add(valor);
    }
  }

  private RelatorioFinanceiroResumoResponse toResumoResponse(
      BigDecimal contasPagas, List<RelatorioContaPagamentoRow> contasPagasPorConta) {
    BigDecimal contasPagasSeguras = contasPagas == null ? BigDecimal.ZERO : contasPagas;
    return new RelatorioFinanceiroResumoResponse(
        receitasFinanceiras,
        receitasEstimaveis,
        totalReceitas,
        despesasConsideradas(),
        despesasAdvocaciaContabilidade,
        totalDespesas,
        despesasTotaisResumo(),
        despesasCombustivel,
        despesasAlimentacao,
        despesasLocacaoVeiculos,
        tetoGastos(LIMITE_COMBUSTIVEL_RATIO),
        tetoGastos(LIMITE_ALIMENTACAO_RATIO),
        tetoGastos(LIMITE_LOCACAO_RATIO),
        contasPagasSeguras,
        contasAPagar(contasPagasSeguras),
        saldoFinal(),
        utilizadoRatio(),
        saldosContas(contasPagasPorConta));
  }

  private List<RelatorioSaldoContaResponse> saldosContas(
      List<RelatorioContaPagamentoRow> contasPagasPorConta) {
    Map<ContaOrigemPagamentoItem, BigDecimal> pagamentosPorConta =
        new EnumMap<>(ContaOrigemPagamentoItem.class);
    if (contasPagasPorConta != null) {
      for (RelatorioContaPagamentoRow pagamento : contasPagasPorConta) {
        if (pagamento == null || pagamento.contaOrigemPagamento() == null) {
          continue;
        }
        pagamentosPorConta.merge(
            pagamento.contaOrigemPagamento(),
            pagamento.totalPago() == null ? BigDecimal.ZERO : pagamento.totalPago(),
            BigDecimal::add);
      }
    }
    return List.of(
        new RelatorioSaldoContaResponse(
            CONTA_DC,
            saldoConta(receitasContaDc, pagamentosPorConta, ContaOrigemPagamentoItem.CONTA_DC)),
        new RelatorioSaldoContaResponse(
            CONTA_FEFC,
            saldoConta(receitasContaFefc, pagamentosPorConta, ContaOrigemPagamentoItem.CONTA_FEFC)),
        new RelatorioSaldoContaResponse(
            CONTA_FP,
            saldoConta(receitasContaFp, pagamentosPorConta, ContaOrigemPagamentoItem.CONTA_FP)));
  }

  private BigDecimal saldoConta(
      BigDecimal receitas,
      Map<ContaOrigemPagamentoItem, BigDecimal> pagamentosPorConta,
      ContaOrigemPagamentoItem conta) {
    return receitas.subtract(pagamentosPorConta.getOrDefault(conta, BigDecimal.ZERO));
  }

  private BigDecimal despesasConsideradas() {
    return totalDespesas.subtract(despesasAdvocaciaContabilidade);
  }

  private BigDecimal despesasBaseLimites() {
    return despesasConsideradas().add(receitasEstimaveis);
  }

  private BigDecimal despesasTotaisResumo() {
    return despesasBaseLimites().add(despesasAdvocaciaContabilidade);
  }

  private BigDecimal tetoGastos(BigDecimal ratio) {
    return despesasBaseLimites().multiply(ratio).setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal saldoFinal() {
    return totalReceitas.subtract(totalDespesas);
  }

  private BigDecimal contasAPagar(BigDecimal contasPagas) {
    BigDecimal contasPagasSeguras = contasPagas == null ? BigDecimal.ZERO : contasPagas;
    return despesasConsideradas().add(despesasAdvocaciaContabilidade).subtract(contasPagasSeguras);
  }

  private BigDecimal utilizadoRatio() {
    if (totalReceitas.signum() == 0) {
      return BigDecimal.ZERO;
    }
    return totalDespesas.divide(totalReceitas, 4, RoundingMode.HALF_UP);
  }

  private BigDecimal percentualSobreDespesas(BigDecimal valorCategoria) {
    if (totalDespesas.signum() == 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal parcial = valorCategoria == null ? BigDecimal.ZERO : valorCategoria;
    return parcial.divide(totalDespesas, 4, RoundingMode.HALF_UP);
  }

  private String normalizeCategoryName(String value) {
    if (value == null) {
      return "";
    }
    String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD);
    normalized = normalized.replaceAll("\\p{M}+", "");
    return normalized.toUpperCase(Locale.ROOT);
  }
}
