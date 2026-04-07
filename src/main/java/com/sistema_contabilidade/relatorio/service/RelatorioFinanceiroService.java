package com.sistema_contabilidade.relatorio.service;

import com.sistema_contabilidade.item.model.Item;
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
import java.util.Comparator;
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
  private static final String COLOR_PRIMARY_BLUE = "#2563eb";
  private static final String COLOR_DANGER_RED = "#ef4444";
  private static final String COLOR_WARNING_AMBER = "#f59e0b";
  private static final String COLOR_SUCCESS_GREEN = "#10b981";
  private static final String COLOR_VIOLET = "#8b5cf6";
  private static final String COLOR_INFO_CYAN = "#0ea5e9";
  private static final String COLOR_ORANGE = "#f97316";
  private static final String COLOR_NEUTRAL_GRAY = "#6b7280";
  private static final double DONUT_RADIUS = 82.0d;
  private static final double DONUT_CIRCUMFERENCE = 2 * Math.PI * DONUT_RADIUS;
  private static final List<String> FALLBACK_CATEGORY_COLORS =
      List.of(
          COLOR_PRIMARY_BLUE,
          COLOR_DANGER_RED,
          COLOR_WARNING_AMBER,
          COLOR_SUCCESS_GREEN,
          COLOR_VIOLET,
          COLOR_INFO_CYAN,
          COLOR_ORANGE);
  private static final Map<String, String> CATEGORY_COLOR_BY_NAME =
      Map.ofEntries(
          Map.entry("PUBLICIDADE POR MATERIAIS IMPRESSOS", COLOR_PRIMARY_BLUE),
          Map.entry("PUBLICIDADE NA INTERNET", COLOR_DANGER_RED),
          Map.entry("PUBLICIDADE POR CARRO DE SOM", COLOR_WARNING_AMBER),
          Map.entry("PRODUCAO DE PROGRAMAS DE RADIO, TV OU VIDEO", COLOR_SUCCESS_GREEN),
          Map.entry("IMPULSIONAMENTO DE CONTEUDO", COLOR_VIOLET),
          Map.entry("SERVICOS PRESTADOS POR TERCEIROS", COLOR_INFO_CYAN),
          Map.entry("SERVICOS ADVOCATICIOS", COLOR_ORANGE),
          Map.entry("SERVICOS CONTABEIS", "#0891b2"),
          Map.entry("ATIVIDADES DE MILITANCIA E MOBILIZACAO DE RUA", "#0f766e"),
          Map.entry("REMUNERACAO DE PESSOAL", "#dc2626"),
          Map.entry("ALUGUEL DE IMOVEIS", COLOR_PRIMARY_BLUE),
          Map.entry("ALUGUEL DE VEICULOS", "#3b82f6"),
          Map.entry("COMBUSTIVEIS E LUBRIFICANTES", "#ea580c"),
          Map.entry("ENERGIA ELETRICA", COLOR_WARNING_AMBER),
          Map.entry("AGUA", "#06b6d4"),
          Map.entry("INTERNET", "#6366f1"),
          Map.entry("TELEFONE", COLOR_VIOLET),
          Map.entry("MATERIAL DE EXPEDIENTE", "#84cc16"),
          Map.entry("MATERIAL DE CAMPANHA (NAO PUBLICITARIO)", "#65a30d"),
          Map.entry("ALIMENTACAO", COLOR_DANGER_RED),
          Map.entry("TRANSPORTE OU DESLOCAMENTO", COLOR_ORANGE),
          Map.entry("HOSPEDAGEM", "#14b8a6"),
          Map.entry("ORGANIZACAO DE EVENTOS", "#a855f7"),
          Map.entry("PRODUCAO DE JINGLES, VINHETAS E SLOGANS", "#ec4899"),
          Map.entry("PRODUCAO DE MATERIAL GRAFICO", "#0284c7"),
          Map.entry("CRIACAO E INCLUSAO DE PAGINAS NA INTERNET", "#4f46e5"),
          Map.entry("MANUTENCAO DE SITES", "#7c3aed"),
          Map.entry("SOFTWARES E FERRAMENTAS DIGITAIS", COLOR_SUCCESS_GREEN),
          Map.entry("TAXAS BANCARIAS", "#475569"),
          Map.entry("ENCARGOS FINANCEIROS", "#334155"),
          Map.entry("MULTAS ELEITORAIS", "#991b1b"),
          Map.entry("DOACOES A OUTROS CANDIDATOS/PARTIDOS", "#7c2d12"),
          Map.entry("BAIXA DE ESTIMAVEIS EM DINHEIRO", "#1d4ed8"),
          Map.entry("OUTRAS DESPESAS", COLOR_NEUTRAL_GRAY),
          Map.entry("ALUGUEL", COLOR_PRIMARY_BLUE),
          Map.entry("ENERGIA", COLOR_WARNING_AMBER),
          Map.entry("SERVICOS", COLOR_INFO_CYAN),
          Map.entry("IMPOSTOS", "#475569"),
          Map.entry("MATERIAIS", "#84cc16"),
          Map.entry("OUTROS", COLOR_NEUTRAL_GRAY));

  private final ItemRepository itemRepository;
  private final RoleRepository roleRepository;
  private final UsuarioRepository usuarioRepository;
  private final PlaywrightPdfService playwrightPdfService;

  public RelatorioFinanceiroResponse gerar(Authentication authentication) {
    return gerar(authentication, null);
  }

  public RelatorioFinanceiroResponse gerar(Authentication authentication, String roleFiltro) {
    String roleFiltroNormalizada = normalizarRole(roleFiltro);
    Set<String> roleNomesAutenticado = extrairRoleNomes(authentication);
    boolean isAdmin = roleNomesAutenticado.contains(ADMIN_ROLE);
    if (roleFiltroNormalizada != null && !isAdmin) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Somente admin pode filtrar relatorio por role.");
    }

    List<Item> itensVisiveis =
        roleFiltroNormalizada == null
            ? buscarItensVisiveis(roleNomesAutenticado)
            : buscarItensPorRoleFiltro(roleFiltroNormalizada);
    List<RelatorioItemDto> receitas = filtrarItensPorTipo(itensVisiveis, TipoItem.RECEITA);
    List<RelatorioItemDto> despesas = filtrarItensPorTipo(itensVisiveis, TipoItem.DESPESA);

    BigDecimal totalReceitas = somarValores(receitas);
    BigDecimal totalDespesas = somarValores(despesas);
    BigDecimal saldoFinal = totalReceitas.subtract(totalDespesas);

    return new RelatorioFinanceiroResponse(
        totalReceitas, totalDespesas, saldoFinal, receitas, despesas);
  }

  public List<String> listarRolesDisponiveis(Authentication authentication) {
    Set<String> roleNomesAutenticado = extrairRoleNomes(authentication);
    if (!roleNomesAutenticado.contains(ADMIN_ROLE)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Somente admin pode listar roles para filtro.");
    }
    return roleRepository.findAll().stream()
        .map(role -> role.getNome())
        .filter(java.util.Objects::nonNull)
        .map(nome -> nome.trim().toUpperCase(Locale.ROOT))
        .filter(nome -> !nome.isBlank())
        .distinct()
        .sorted()
        .toList();
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
        "Sistema Contabilidade",
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
    for (Map.Entry<String, BigDecimal> entry : orderedEntries) {
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
              colorForCategory(entry.getKey())));
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

  private List<Item> buscarItensVisiveis(Set<String> roleNomes) {
    if (roleNomes.contains(ADMIN_ROLE)) {
      return itemRepository.findAll();
    }
    if (roleNomes.isEmpty()) {
      return List.of();
    }
    return itemRepository.findAllVisiveisPorRoleNomes(roleNomes);
  }

  private List<Item> buscarItensPorRoleFiltro(String roleFiltro) {
    return itemRepository.findAllVisiveisPorRoleNomes(Set.of(roleFiltro));
  }

  private List<RelatorioItemDto> filtrarItensPorTipo(List<Item> itens, TipoItem tipo) {
    return itens.stream()
        .filter(item -> item.getTipo() == tipo)
        .map(RelatorioItemDto::from)
        .sorted(
            Comparator.comparing(
                    RelatorioItemDto::data, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                    RelatorioItemDto::horarioCriacao,
                    Comparator.nullsLast(Comparator.reverseOrder())))
        .toList();
  }

  private BigDecimal somarValores(List<RelatorioItemDto> itens) {
    return itens.stream()
        .map(RelatorioItemDto::valor)
        .filter(java.util.Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
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
      return receita ? "Lancamento de " + categoriaSegura.toLowerCase(Locale.ROOT) : categoriaSegura;
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

  private String colorForCategory(String categoryName) {
    String normalized = normalizeCategoryName(categoryName);
    String mapped = CATEGORY_COLOR_BY_NAME.get(normalized);
    if (mapped != null) {
      return mapped;
    }
    if (normalized.isBlank()) {
      return COLOR_NEUTRAL_GRAY;
    }
    int index = Math.floorMod(normalized.hashCode(), FALLBACK_CATEGORY_COLORS.size());
    return FALLBACK_CATEGORY_COLORS.get(index);
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
