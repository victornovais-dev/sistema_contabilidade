package com.sistema_contabilidade.relatorio.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class RelatorioCategoriaCorPalette {

  private static final String COLOR_RED = "#E6194B";
  private static final String COLOR_GREEN = "#3CB44B";
  private static final String COLOR_BLUE = "#4363D8";
  private static final String COLOR_ORANGE = "#F58231";
  private static final String COLOR_PURPLE = "#911EB4";
  private static final String COLOR_CYAN = "#46F0F0";
  private static final String COLOR_MAGENTA = "#F032E6";

  private static final List<String> FALLBACK_CATEGORY_COLORS =
      List.of(
          COLOR_RED,
          COLOR_GREEN,
          COLOR_BLUE,
          COLOR_ORANGE,
          COLOR_PURPLE,
          COLOR_CYAN,
          COLOR_MAGENTA,
          "#808000",
          "#FFD8B1",
          "#008080",
          "#000075",
          "#FFB300",
          "#800000",
          "#A6CEE3",
          "#AAFFC3",
          "#9A6324",
          "#DCBEFF",
          "#469990",
          "#FF6F00",
          "#A65628",
          "#00B4D8",
          "#B10DC9",
          "#7FDBFF",
          "#FFDC00",
          "#2ECC40",
          "#85144B",
          "#39CCCC",
          "#111111",
          "#6A3D9A",
          "#00A896",
          "#FF4136",
          "#0074D9",
          "#B8860B");

  private static final Map<String, String> CATEGORY_COLOR_BY_NAME =
      Map.ofEntries(
          Map.entry("PUBLICIDADE POR MATERIAIS IMPRESSOS", COLOR_RED),
          Map.entry("PUBLICIDADE NA INTERNET", COLOR_GREEN),
          Map.entry("PUBLICIDADE POR CARRO DE SOM", COLOR_BLUE),
          Map.entry("PRODUCAO DE PROGRAMAS DE RADIO, TV OU VIDEO", COLOR_ORANGE),
          Map.entry("IMPULSIONAMENTO DE CONTEUDO", COLOR_PURPLE),
          Map.entry("SERVICOS PRESTADOS POR TERCEIROS", COLOR_CYAN),
          Map.entry("SERVICOS ADVOCATICIOS", COLOR_MAGENTA),
          Map.entry("SERVICOS CONTABEIS", "#808000"),
          Map.entry("ATIVIDADES DE MILITANCIA E MOBILIZACAO DE RUA", "#FFD8B1"),
          Map.entry("REMUNERACAO DE PESSOAL", "#008080"),
          Map.entry("ALUGUEL DE IMOVEIS", "#000075"),
          Map.entry("ALUGUEL DE VEICULOS", "#FFB300"),
          Map.entry("COMBUSTIVEIS E LUBRIFICANTES", "#800000"),
          Map.entry("ENERGIA ELETRICA", "#A6CEE3"),
          Map.entry("AGUA", "#AAFFC3"),
          Map.entry("INTERNET", "#9A6324"),
          Map.entry("TELEFONE", "#DCBEFF"),
          Map.entry("MATERIAL DE EXPEDIENTE", "#469990"),
          Map.entry("MATERIAL DE CAMPANHA (NAO PUBLICITARIO)", "#FF6F00"),
          Map.entry("ALIMENTACAO", "#A65628"),
          Map.entry("TRANSPORTE OU DESLOCAMENTO", "#00B4D8"),
          Map.entry("HOSPEDAGEM", "#B10DC9"),
          Map.entry("ORGANIZACAO DE EVENTOS", "#7FDBFF"),
          Map.entry("PRODUCAO DE JINGLES, VINHETAS E SLOGANS", "#FFDC00"),
          Map.entry("PRODUCAO DE MATERIAL GRAFICO", "#2ECC40"),
          Map.entry("CRIACAO E INCLUSAO DE PAGINAS NA INTERNET", "#85144B"),
          Map.entry("MANUTENCAO DE SITES", "#39CCCC"),
          Map.entry("SOFTWARES E FERRAMENTAS DIGITAIS", "#111111"),
          Map.entry("TAXAS BANCARIAS", "#6A3D9A"),
          Map.entry("ENCARGOS FINANCEIROS", "#00A896"),
          Map.entry("MULTAS ELEITORAIS", "#FF4136"),
          Map.entry("DOACOES A OUTROS CANDIDATOS/PARTIDOS", "#0074D9"),
          Map.entry("BAIXA DE ESTIMAVEIS EM DINHEIRO", "#B8860B"),
          Map.entry("OUTRAS DESPESAS", COLOR_RED),
          Map.entry("ALUGUEL", COLOR_GREEN),
          Map.entry("ENERGIA", COLOR_BLUE),
          Map.entry("SERVICOS", COLOR_ORANGE),
          Map.entry("IMPOSTOS", COLOR_PURPLE),
          Map.entry("MATERIAIS", COLOR_CYAN),
          Map.entry("OUTROS", COLOR_MAGENTA));

  String colorForCategory(String categoryName, int index) {
    String normalized = normalizeCategoryName(categoryName);
    String mapped = CATEGORY_COLOR_BY_NAME.get(normalized);
    if (mapped != null) {
      return mapped;
    }
    if (FALLBACK_CATEGORY_COLORS.isEmpty()) {
      return "#6b7280";
    }
    int fallbackIndex = Math.floorMod(index, FALLBACK_CATEGORY_COLORS.size());
    return FALLBACK_CATEGORY_COLORS.get(fallbackIndex);
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
