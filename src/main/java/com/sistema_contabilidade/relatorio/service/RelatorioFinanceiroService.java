package com.sistema_contabilidade.relatorio.service;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResponse;
import com.sistema_contabilidade.relatorio.dto.RelatorioItemDto;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RelatorioFinanceiroService {

  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"));
  private static final BigDecimal CEM = BigDecimal.valueOf(100);
  private static final int ROLE_BUDGET_PARTS = 2;
  private static final float PAGE_TOP = 780f;
  private static final float PAGE_LEFT = 50f;
  private static final float LINE_HEIGHT = 16f;
  private static final String ADMIN_ROLE = "ADMIN";

  private final ItemRepository itemRepository;
  private final RoleRepository roleRepository;

  @Value("${app.relatorio.orcamento.roles:ADMIN:20000000}")
  private String roleBudgetConfig;

  @Value("${app.relatorio.orcamento.default:0}")
  private BigDecimal defaultBudget;

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

    BigDecimal orcamento =
        roleFiltroNormalizada == null
            ? calcularOrcamento(roleNomesAutenticado)
            : calcularOrcamento(Set.of(roleFiltroNormalizada));
    BigDecimal utilizadoPercentual = calcularUtilizadoPercentual(orcamento, totalDespesas);

    return new RelatorioFinanceiroResponse(
        orcamento,
        totalReceitas,
        totalDespesas,
        saldoFinal,
        utilizadoPercentual,
        receitas,
        despesas);
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

  public byte[] gerarPdf(RelatorioFinanceiroResponse relatorio) {
    try (PDDocument document = new PDDocument()) {
      List<PdfLine> lines = montarLinhasPdf(relatorio);
      int maxLinesPerPage = 43;

      for (int start = 0; start < lines.size(); start += maxLinesPerPage) {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
          float y = PAGE_TOP;
          int end = Math.min(start + maxLinesPerPage, lines.size());
          for (int index = start; index < end; index++) {
            PdfLine line = lines.get(index);
            y = writeLine(content, line.bold() ? 12 : 10, PAGE_LEFT, y, line.value(), line.bold());
          }
        }
      }

      java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
      document.save(output);
      return output.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException("Falha ao gerar PDF do relatorio", exception);
    }
  }

  private List<PdfLine> montarLinhasPdf(RelatorioFinanceiroResponse relatorio) {
    List<PdfLine> lines = new ArrayList<>();
    lines.add(new PdfLine(true, "Relatorio Financeiro"));
    lines.add(new PdfLine(false, "Gerado em: " + DATE_TIME_FORMATTER.format(LocalDateTime.now())));
    lines.add(new PdfLine(false, ""));
    lines.add(new PdfLine(true, "Resumo"));
    lines.add(new PdfLine(false, "Total de receitas: " + moeda(relatorio.totalReceitas())));
    lines.add(new PdfLine(false, "Total de despesas: " + moeda(relatorio.totalDespesas())));
    lines.add(new PdfLine(false, "Orcamento: " + moeda(relatorio.orcamento())));
    lines.add(new PdfLine(false, "Utilizado: " + percentual(relatorio.utilizadoPercentual())));
    lines.add(new PdfLine(false, "Saldo final: " + moeda(relatorio.saldoFinal())));
    lines.add(new PdfLine(false, ""));
    lines.add(new PdfLine(true, "Receitas"));
    lines.addAll(montarLinhasItens(relatorio.receitas(), "receita"));
    lines.add(new PdfLine(false, ""));
    lines.add(new PdfLine(true, "Despesas"));
    lines.addAll(montarLinhasItens(relatorio.despesas(), "despesa"));
    return lines;
  }

  private List<PdfLine> montarLinhasItens(List<RelatorioItemDto> itens, String tipoDescricao) {
    if (itens.isEmpty()) {
      return List.of(new PdfLine(false, "Sem itens de " + tipoDescricao + "."));
    }
    return itens.stream()
        .map(
            item ->
                new PdfLine(
                    false,
                    String.format(
                        Locale.forLanguageTag("pt-BR"),
                        "%s | %s | %s",
                        item.data() != null
                            ? item.data().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            : "-",
                        item.horarioCriacao() != null
                            ? item.horarioCriacao().format(DateTimeFormatter.ofPattern("HH:mm"))
                            : "-",
                        moeda(item.valor()))))
        .toList();
  }

  private float writeLine(
      PDPageContentStream content, int fontSize, float x, float y, String text, boolean bold) {
    PDType1Font font =
        new PDType1Font(
            bold ? Standard14Fonts.FontName.HELVETICA_BOLD : Standard14Fonts.FontName.HELVETICA);
    try {
      content.beginText();
      content.setFont(font, fontSize);
      content.newLineAtOffset(x, y);
      content.showText(text);
      content.endText();
      return y - LINE_HEIGHT;
    } catch (IOException exception) {
      throw new IllegalStateException("Falha ao escrever linha no PDF", exception);
    }
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
    if (authentication == null || authentication.getAuthorities() == null) {
      return Set.of();
    }
    return authentication.getAuthorities().stream()
        .map(authority -> authority.getAuthority())
        .filter(authority -> authority != null && authority.startsWith("ROLE_"))
        .map(authority -> authority.substring("ROLE_".length()))
        .collect(Collectors.toSet());
  }

  private BigDecimal calcularOrcamento(Set<String> roleNomes) {
    if (roleNomes.isEmpty()) {
      return defaultBudget.max(BigDecimal.ZERO);
    }
    Map<String, BigDecimal> roleBudgets = parseRoleBudgetConfig();
    return roleNomes.stream()
        .map(roleBudgets::get)
        .filter(java.util.Objects::nonNull)
        .max(BigDecimal::compareTo)
        .orElse(defaultBudget)
        .max(BigDecimal.ZERO);
  }

  private String normalizarRole(String role) {
    if (role == null || role.isBlank()) {
      return null;
    }
    return role.trim().toUpperCase(Locale.ROOT);
  }

  private Map<String, BigDecimal> parseRoleBudgetConfig() {
    Map<String, BigDecimal> result = new LinkedHashMap<>();
    if (roleBudgetConfig == null || roleBudgetConfig.isBlank()) {
      return result;
    }
    String[] entries = roleBudgetConfig.split(",");
    for (String entry : entries) {
      String[] parts = entry.split(":");
      if (parts.length != ROLE_BUDGET_PARTS) {
        continue;
      }
      String role = parts[0].trim().toUpperCase(Locale.ROOT);
      String value = parts[1].trim();
      if (role.isBlank() || value.isBlank()) {
        continue;
      }
      try {
        result.put(role, new BigDecimal(value));
      } catch (NumberFormatException ignored) {
        // Ignora entradas invalidas.
      }
    }
    return result;
  }

  private BigDecimal calcularUtilizadoPercentual(BigDecimal orcamento, BigDecimal totalDespesas) {
    if (orcamento == null || orcamento.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }
    return totalDespesas.multiply(CEM).divide(orcamento, 2, RoundingMode.HALF_UP);
  }

  private String moeda(BigDecimal valor) {
    return NumberFormatProvider.currency().format(valor == null ? BigDecimal.ZERO : valor);
  }

  private String percentual(BigDecimal valor) {
    BigDecimal percentual = valor == null ? BigDecimal.ZERO : valor;
    return NumberFormatProvider.percent().format(percentual) + "%";
  }

  private record PdfLine(boolean bold, String value) {}

  private static final class NumberFormatProvider {
    private static java.text.DecimalFormat currency() {
      java.text.DecimalFormat format =
          (java.text.DecimalFormat)
              java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));
      format.setMaximumFractionDigits(2);
      format.setMinimumFractionDigits(2);
      return format;
    }

    private static java.text.DecimalFormat percent() {
      java.text.DecimalFormat format = new java.text.DecimalFormat("0.00");
      format.setRoundingMode(RoundingMode.HALF_UP);
      return format;
    }
  }
}
