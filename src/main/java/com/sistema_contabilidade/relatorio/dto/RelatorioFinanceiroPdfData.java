package com.sistema_contabilidade.relatorio.dto;

import java.util.List;

public record RelatorioFinanceiroPdfData(
    String empresa,
    String periodo,
    String responsavel,
    String dataEmissao,
    String totalReceitas,
    String totalDespesas,
    String saldoFinal,
    String resultadoDescricao,
    String observacoes,
    List<ItemLinha> receitas,
    List<ItemLinha> despesas,
    List<CategoriaResumo> categoriasDespesa,
    List<DonutSlice> donutSlices) {

  public RelatorioFinanceiroPdfData {
    receitas = receitas == null ? List.of() : List.copyOf(receitas);
    despesas = despesas == null ? List.of() : List.copyOf(despesas);
    categoriasDespesa = categoriasDespesa == null ? List.of() : List.copyOf(categoriasDespesa);
    donutSlices = donutSlices == null ? List.of() : List.copyOf(donutSlices);
  }

  public record ItemLinha(
      String data, String descricao, String categoria, String horario, String valor) {}

  public record CategoriaResumo(String nome, String valor, String percentual, String cor) {}

  public record DonutSlice(String cor, String dashArray, String dashOffset) {}
}
