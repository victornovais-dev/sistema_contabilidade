package com.sistema_contabilidade.relatorio.service;

import com.sistema_contabilidade.common.util.CandidateRoleUtils;
import com.sistema_contabilidade.common.util.RevenueClassificationUtils;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroPdfData;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResponse;
import com.sistema_contabilidade.relatorio.dto.RelatorioItemDto;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
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
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RelatorioFinanceiroService {

  private static final Locale LOCALE_PT_BR = Locale.forLanguageTag("pt-BR");
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", LOCALE_PT_BR);
  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE_PT_BR);
  private static final DateTimeFormatter TIME_FORMATTER =
      DateTimeFormatter.ofPattern("HH:mm", LOCALE_PT_BR);
  private static final String ADMIN_ROLE = "ADMIN";
  private static final Set<String> DESPESAS_ADVOCACIA_CONTABILIDADE =
      Set.of("SERVICOS ADVOCATICIOS", "SERVICOS CONTABEIS");
  private static final Set<String> DESPESAS_COMBUSTIVEL = Set.of("COMBUSTIVEIS E LUBRIFICANTES");
  private static final Set<String> DESPESAS_ALIMENTACAO = Set.of("ALIMENTACAO");
  private static final Set<String> DESPESAS_LOCACAO = Set.of("ALUGUEL DE VEICULOS");
  private static final double DONUT_RADIUS = 82.0d;
  private static final double DONUT_CIRCUMFERENCE = 2 * Math.PI * DONUT_RADIUS;
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

  private final ItemRepository itemRepository;
  private final RoleRepository roleRepository;
  private final UsuarioRepository usuarioRepository;
  private final PlaywrightPdfService playwrightPdfService;

  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public RelatorioFinanceiroResponse gerar(Authentication authentication) {
    return gerarRelatorio(authentication, null);
  }

  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public RelatorioFinanceiroResponse gerar(Authentication authentication, String roleFiltro) {
    return gerarRelatorio(authentication, roleFiltro);
  }

  private RelatorioFinanceiroResponse gerarRelatorio(
      Authentication authentication, String roleFiltro) {
    String roleFiltroNormalizada = normalizarRole(roleFiltro);
    Set<String> roleNomesAutenticado = extrairRoleNomes(authentication);
    boolean isAdmin = roleNomesAutenticado.contains(ADMIN_ROLE);
    if (roleFiltroNormalizada != null
        && !isAdmin
        && !roleNomesAutenticado.contains(roleFiltroNormalizada)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "A role selecionada nao pertence ao usuario autenticado.");
    }

    List<RelatorioItemDto> itensVisiveis =
        roleFiltroNormalizada == null
            ? buscarItensVisiveis(roleNomesAutenticado)
            : buscarItensPorRoleFiltro(roleFiltroNormalizada);
    List<RelatorioItemDto> receitas = filtrarItensPorTipo(itensVisiveis, TipoItem.RECEITA);
    List<RelatorioItemDto> despesas = filtrarItensPorTipo(itensVisiveis, TipoItem.DESPESA);

    BigDecimal receitasFinanceiras = somarReceitasPorCategoria(receitas, this::isFinancialRevenue);
    BigDecimal receitasEstimaveis = somarReceitasPorCategoria(receitas, this::isEstimatedRevenue);
    BigDecimal totalReceitas = somarValores(receitas);
    BigDecimal despesasAdvocaciaContabilidade =
        somarDespesasPorCategoria(despesas, this::isAdvocaciaOrContabilidadeExpense);
    BigDecimal totalDespesas = somarValores(despesas);
    BigDecimal despesasConsideradas = totalDespesas.subtract(despesasAdvocaciaContabilidade);
    BigDecimal despesasCombustivel =
        somarDespesasPorCategoria(despesas, this::isCombustivelExpense);
    BigDecimal despesasAlimentacao =
        somarDespesasPorCategoria(despesas, this::isAlimentacaoExpense);
    BigDecimal despesasLocacao = somarDespesasPorCategoria(despesas, this::isLocacaoExpense);
    BigDecimal limiteGastosCombustivelPercentual =
        calcularPercentualSobreDespesas(despesasCombustivel, totalDespesas);
    BigDecimal limiteGastosAlimentacaoPercentual =
        calcularPercentualSobreDespesas(despesasAlimentacao, totalDespesas);
    BigDecimal limiteGastosLocacaoPercentual =
        calcularPercentualSobreDespesas(despesasLocacao, totalDespesas);
    BigDecimal saldoFinal = totalReceitas.subtract(totalDespesas);

    return new RelatorioFinanceiroResponse(
        receitasFinanceiras,
        receitasEstimaveis,
        totalReceitas,
        despesasConsideradas,
        despesasAdvocaciaContabilidade,
        totalDespesas,
        limiteGastosCombustivelPercentual,
        limiteGastosAlimentacaoPercentual,
        limiteGastosLocacaoPercentual,
        saldoFinal,
        receitas,
        despesas);
  }

  public List<String> listarRolesDisponiveis(Authentication authentication) {
    Set<String> roleNomesAutenticado = extrairRoleNomes(authentication);
    if (roleNomesAutenticado.contains(ADMIN_ROLE)) {
      return CandidateRoleUtils.filterCandidateRoles(roleRepository.findAllRoleNamesOrdered());
    }
    return CandidateRoleUtils.filterCandidateRoles(roleNomesAutenticado);
  }

  public byte[] gerarPdf(Authentication authentication, RelatorioFinanceiroResponse relatorio) {
    return playwrightPdfService.generateFinancialReportPdf(buildPdfData(authentication, relatorio));
  }

  private RelatorioFinanceiroPdfData buildPdfData(
      Authentication authentication, RelatorioFinanceiroResponse relatorio) {
    List<RelatorioFinanceiroPdfData.ItemLinha> receitas = buildPdfItems(relatorio.receitas(), true);
    List<RelatorioFinanceiroPdfData.ItemLinha> despesas =
        buildPdfItems(relatorio.despesas(), false);
    List<RelatorioFinanceiroPdfData.CategoriaResumo> categoriasDespesa =
        buildExpenseCategories(relatorio.despesas(), relatorio.totalDespesas());
    return new RelatorioFinanceiroPdfData(
        "SACS CONTÁBIL",
        buildPeriodo(relatorio),
        extrairNomeResponsavel(authentication),
        DATE_TIME_FORMATTER.format(LocalDateTime.now()),
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
              colorForCategory(entry.getKey(), index)));
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

  private String extrairNomeResponsavel(Authentication authentication) {
    if (authentication == null
        || authentication.getName() == null
        || authentication.getName().isBlank()) {
      return "Usuario autenticado";
    }
    return usuarioRepository
        .findByEmail(authentication.getName())
        .map(usuario -> usuario.getNome())
        .filter(nome -> nome != null && !nome.isBlank())
        .orElse(authentication.getName());
  }

  private List<RelatorioItemDto> buscarItensVisiveis(Set<String> roleNomes) {
    if (roleNomes.contains(ADMIN_ROLE)) {
      return itemRepository.findAllRelatorioItensOrderByDataDescHorarioCriacaoDesc();
    }
    if (roleNomes.isEmpty()) {
      return List.of();
    }
    return itemRepository.findRelatorioItensByRoleNomesOrderByDataDescHorarioCriacaoDesc(roleNomes);
  }

  private List<RelatorioItemDto> buscarItensPorRoleFiltro(String roleFiltro) {
    return itemRepository.findRelatorioItensByRoleNomeOrderByDataDescHorarioCriacaoDesc(roleFiltro);
  }

  private List<RelatorioItemDto> filtrarItensPorTipo(List<RelatorioItemDto> itens, TipoItem tipo) {
    return itens.stream().filter(item -> item.tipo() == tipo).toList();
  }

  private BigDecimal somarValores(List<RelatorioItemDto> itens) {
    return itens.stream()
        .map(RelatorioItemDto::valor)
        .filter(java.util.Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal somarReceitasPorCategoria(
      List<RelatorioItemDto> receitas, java.util.function.Predicate<String> matcher) {
    return receitas.stream()
        .filter(item -> matcher.test(item.descricao()))
        .map(RelatorioItemDto::valor)
        .filter(java.util.Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal somarDespesasPorCategoria(
      List<RelatorioItemDto> despesas, java.util.function.Predicate<String> matcher) {
    return despesas.stream()
        .filter(item -> matcher.test(item.descricao()))
        .map(RelatorioItemDto::valor)
        .filter(java.util.Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private boolean isFinancialRevenue(String descricao) {
    return RevenueClassificationUtils.isFinancialRevenue(descricao);
  }

  private boolean isEstimatedRevenue(String descricao) {
    return RevenueClassificationUtils.isEstimatedRevenue(descricao);
  }

  private boolean isAdvocaciaOrContabilidadeExpense(String descricao) {
    return DESPESAS_ADVOCACIA_CONTABILIDADE.contains(normalizeCategoryName(descricao));
  }

  private boolean isCombustivelExpense(String descricao) {
    return DESPESAS_COMBUSTIVEL.contains(normalizeCategoryName(descricao));
  }

  private boolean isAlimentacaoExpense(String descricao) {
    return DESPESAS_ALIMENTACAO.contains(normalizeCategoryName(descricao));
  }

  private boolean isLocacaoExpense(String descricao) {
    return DESPESAS_LOCACAO.contains(normalizeCategoryName(descricao));
  }

  private BigDecimal calcularPercentualSobreDespesas(
      BigDecimal valorCategoria, BigDecimal totalDespesas) {
    BigDecimal total = totalDespesas == null ? BigDecimal.ZERO : totalDespesas;
    BigDecimal parcial = valorCategoria == null ? BigDecimal.ZERO : valorCategoria;
    if (total.signum() == 0) {
      return BigDecimal.ZERO;
    }
    return parcial.divide(total, 4, RoundingMode.HALF_UP);
  }

  private Set<String> extrairRoleNomes(Authentication authentication) {
    if (authentication == null) {
      return Set.of();
    }
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(authority -> authority != null && authority.startsWith("ROLE_"))
        .map(authority -> authority.substring("ROLE_".length()))
        .collect(java.util.stream.Collectors.toSet());
  }

  private String normalizarRole(String role) {
    if (role == null || role.isBlank()) {
      return null;
    }
    return role.trim().toUpperCase(Locale.ROOT);
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

  private String colorForCategory(String categoryName, int index) {
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
    String normalized = java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFD);
    normalized = normalized.replaceAll("\\p{M}+", "");
    return normalized.toUpperCase(Locale.ROOT);
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
