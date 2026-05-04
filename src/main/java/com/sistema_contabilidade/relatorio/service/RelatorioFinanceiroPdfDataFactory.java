package com.sistema_contabilidade.relatorio.service;

import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroPdfData;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResponse;
import com.sistema_contabilidade.relatorio.dto.RelatorioItemDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class RelatorioFinanceiroPdfDataFactory {

  private static final Locale LOCALE_PT_BR = Locale.forLanguageTag("pt-BR");
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", LOCALE_PT_BR);
  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE_PT_BR);
  private static final DateTimeFormatter TIME_FORMATTER =
      DateTimeFormatter.ofPattern("HH:mm", LOCALE_PT_BR);
  private static final double DONUT_RADIUS = 82.0d;
  private static final double DONUT_CIRCUMFERENCE = 2 * Math.PI * DONUT_RADIUS;
  private final RelatorioCategoriaCorPalette categoriaCorPalette =
      new RelatorioCategoriaCorPalette();

  RelatorioFinanceiroPdfData create(
      String responsavel, RelatorioFinanceiroResponse relatorio, LocalDateTime geradoEm) {
    List<RelatorioFinanceiroPdfData.ItemLinha> receitas = buildPdfItems(relatorio.receitas(), true);
    List<RelatorioFinanceiroPdfData.ItemLinha> despesas =
        buildPdfItems(relatorio.despesas(), false);
    List<RelatorioFinanceiroPdfData.CategoriaResumo> categoriasDespesa =
        buildExpenseCategories(relatorio.despesas(), relatorio.totalDespesas());
    return new RelatorioFinanceiroPdfData(
        "SACS CONTABIL",
        buildPeriodo(relatorio),
        responsavel,
        DATE_TIME_FORMATTER.format(geradoEm),
        moeda(relatorio.totalReceitas()),
        moeda(relatorio.totalDespesas()),
        moeda(relatorio.saldoFinal()),
        buildResultadoDescricao(relatorio),
        buildObservacoes(relatorio),
        receitas,
        despesas,
        categoriasDespesa,
        buildDonutSlices(categoriasDespesa));
  }

  private List<RelatorioFinanceiroPdfData.ItemLinha> buildPdfItems(
      List<RelatorioItemDto> itens, boolean receita) {
    if (itens == null || itens.isEmpty()) {
      return List.of(
          new RelatorioFinanceiroPdfData.ItemLinha(
              "-",
              receita ? "Sem receitas registradas" : "Sem despesas registradas",
              "-",
              "-",
              moeda(BigDecimal.ZERO)));
    }
    return itens.stream()
        .map(
            item ->
                new RelatorioFinanceiroPdfData.ItemLinha(
                    formatDate(item.data()),
                    buildDescricaoDetalhada(item, receita),
                    formatCategoria(item.descricao(), item.tipo()),
                    formatTime(item.horarioCriacao()),
                    moeda(item.valor())))
        .toList();
  }

  private List<RelatorioFinanceiroPdfData.CategoriaResumo> buildExpenseCategories(
      List<RelatorioItemDto> despesas, BigDecimal totalDespesas) {
    if (despesas == null || despesas.isEmpty()) {
      return List.of();
    }
    Map<String, BigDecimal> grouped = new LinkedHashMap<>();
    despesas.forEach(
        item ->
            grouped.merge(
                formatDescricao(item.descricao(), item.tipo()),
                item.valor() == null ? BigDecimal.ZERO : item.valor(),
                BigDecimal::add));

    BigDecimal total = totalDespesas == null ? BigDecimal.ZERO : totalDespesas;
    List<Map.Entry<String, BigDecimal>> orderedEntries =
        grouped.entrySet().stream()
            .sorted(
                Map.Entry.<String, BigDecimal>comparingByValue()
                    .reversed()
                    .thenComparing(Map.Entry.comparingByKey()))
            .toList();

    List<RelatorioFinanceiroPdfData.CategoriaResumo> categorias = new ArrayList<>();
    for (int index = 0; index < orderedEntries.size(); index++) {
      Map.Entry<String, BigDecimal> entry = orderedEntries.get(index);
      BigDecimal percentual =
          total.signum() == 0
              ? BigDecimal.ZERO
              : entry
                  .getValue()
                  .multiply(BigDecimal.valueOf(100))
                  .divide(total, 2, RoundingMode.HALF_UP);
      categorias.add(
          new RelatorioFinanceiroPdfData.CategoriaResumo(
              entry.getKey(),
              moeda(entry.getValue()),
              percentualNumero(percentual) + "%",
              categoriaCorPalette.colorForCategory(entry.getKey(), index)));
    }
    return categorias;
  }

  private List<RelatorioFinanceiroPdfData.DonutSlice> buildDonutSlices(
      List<RelatorioFinanceiroPdfData.CategoriaResumo> categorias) {
    if (categorias == null || categorias.isEmpty()) {
      return List.of();
    }
    List<RelatorioFinanceiroPdfData.DonutSlice> slices = new ArrayList<>();
    double offset = 0.0d;
    for (RelatorioFinanceiroPdfData.CategoriaResumo categoria : categorias) {
      double percentual =
          Double.parseDouble(categoria.percentual().replace("%", "").replace(",", "."));
      double slice = (percentual / 100.0d) * DONUT_CIRCUMFERENCE;
      double remaining = DONUT_CIRCUMFERENCE - slice;
      slices.add(
          new RelatorioFinanceiroPdfData.DonutSlice(
              categoria.cor(), decimal(slice) + " " + decimal(remaining), "-" + decimal(offset)));
      offset += slice;
    }
    return slices;
  }

  private String buildPeriodo(RelatorioFinanceiroResponse relatorio) {
    List<LocalDate> dates =
        List.of(relatorio.receitas(), relatorio.despesas()).stream()
            .flatMap(List::stream)
            .map(RelatorioItemDto::data)
            .filter(java.util.Objects::nonNull)
            .sorted()
            .toList();
    if (dates.isEmpty()) {
      return "Sem periodo definido";
    }
    LocalDate start = dates.getFirst();
    LocalDate end = dates.getLast();
    if (start.equals(end)) {
      return DATE_FORMATTER.format(start);
    }
    return DATE_FORMATTER.format(start) + " a " + DATE_FORMATTER.format(end);
  }

  private String buildResultadoDescricao(RelatorioFinanceiroResponse relatorio) {
    BigDecimal saldo = relatorio.saldoFinal() == null ? BigDecimal.ZERO : relatorio.saldoFinal();
    if (saldo.signum() > 0) {
      return "O periodo fechou com saldo positivo e receita acima das despesas.";
    }
    if (saldo.signum() < 0) {
      return "O periodo fechou com saldo negativo e exige atencao no controle de custos.";
    }
    return "O periodo fechou em equilibrio entre entradas e saidas.";
  }

  private String buildObservacoes(RelatorioFinanceiroResponse relatorio) {
    int totalReceitas = relatorio.receitas() == null ? 0 : relatorio.receitas().size();
    int totalDespesas = relatorio.despesas() == null ? 0 : relatorio.despesas().size();
    return String.format(
        LOCALE_PT_BR,
        "Este documento foi gerado com base em %d receitas e %d despesas visiveis para o usuario autenticado.",
        totalReceitas,
        totalDespesas);
  }

  private String moeda(BigDecimal valor) {
    return NumberFormatProvider.currency().format(valor == null ? BigDecimal.ZERO : valor);
  }

  private String formatDate(LocalDate date) {
    return date == null ? "-" : DATE_FORMATTER.format(date);
  }

  private String formatTime(LocalDateTime dateTime) {
    return dateTime == null ? "-" : TIME_FORMATTER.format(dateTime);
  }

  private String formatDescricao(String descricao, TipoItem tipo) {
    String descricaoNormalizada = descricao == null ? "" : descricao.trim();
    String key = descricaoNormalizada.toUpperCase(Locale.ROOT);
    Map<String, String> labels =
        Map.of(
            "ALUGUEL", "Aluguel",
            "ENERGIA", "Energia eletrica",
            "AGUA", "Agua",
            "SERVICOS", "Servicos",
            "IMPOSTOS", "Impostos",
            "MATERIAIS", "Materiais",
            "OUTROS", "Outros");
    if (labels.containsKey(key)) {
      return labels.get(key);
    }
    if (!key.isBlank()) {
      return descricaoNormalizada;
    }
    return tipo == TipoItem.RECEITA ? "Receita sem descricao" : "Despesa sem descricao";
  }

  private String formatCategoria(String descricao, TipoItem tipo) {
    return formatDescricao(descricao, tipo);
  }

  private String buildDescricaoDetalhada(RelatorioItemDto item, boolean receita) {
    String categoria = formatCategoria(item.descricao(), item.tipo());
    if (item.descricao() != null && !item.descricao().isBlank()) {
      String categoriaPadrao = receita ? "receita" : "despesa";
      String categoriaSegura = categoria == null ? categoriaPadrao : categoria;
      return receita
          ? "Lancamento de " + categoriaSegura.toLowerCase(Locale.ROOT)
          : categoriaSegura;
    }
    return receita ? "Receita registrada no periodo" : "Despesa registrada no periodo";
  }

  private String percentualNumero(BigDecimal percentual) {
    DecimalFormat format = (DecimalFormat) NumberFormat.getNumberInstance(LOCALE_PT_BR);
    format.setMinimumFractionDigits(2);
    format.setMaximumFractionDigits(2);
    return format.format(percentual == null ? BigDecimal.ZERO : percentual);
  }

  private String decimal(double value) {
    return String.format(Locale.US, "%.2f", value);
  }

  private static final class NumberFormatProvider {
    private static DecimalFormat currency() {
      DecimalFormat format = (DecimalFormat) NumberFormat.getCurrencyInstance(LOCALE_PT_BR);
      format.setMaximumFractionDigits(2);
      format.setMinimumFractionDigits(2);
      return format;
    }
  }
}
