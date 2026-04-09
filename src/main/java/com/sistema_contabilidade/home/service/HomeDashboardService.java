package com.sistema_contabilidade.home.service;

import com.sistema_contabilidade.home.dto.HomeDashboardMonthResponse;
import com.sistema_contabilidade.home.dto.HomeDashboardResponse;
import com.sistema_contabilidade.home.dto.HomeLatestLaunchResponse;
import com.sistema_contabilidade.home.dto.HomeMonthlyBalanceRow;
import com.sistema_contabilidade.home.dto.HomeTypeTotalRow;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class HomeDashboardService {

  private static final String ADMIN_ROLE = "ADMIN";
  private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
  private static final int CHART_MONTHS = 6;
  private static final int LATEST_LIMIT = 4;

  private final ItemRepository itemRepository;
  private final UsuarioRepository usuarioRepository;

  @Transactional(readOnly = true)
  public HomeDashboardResponse getDashboard(Authentication authentication, String roleFiltro) {
    DashboardScope scope = resolveScope(authentication, roleFiltro);
    List<HomeTypeTotalRow> totals = loadTypeTotals(scope);
    BigDecimal totalReceitas = sumByType(totals, TipoItem.RECEITA);
    BigDecimal totalDespesas = sumByType(totals, TipoItem.DESPESA);
    BigDecimal saldoFinal = totalReceitas.subtract(totalDespesas);

    LocalDate startDate = YearMonth.now().minusMonths(CHART_MONTHS - 1L).atDay(1);
    List<HomeMonthlyBalanceRow> monthlyRows = loadMonthlyRows(scope, startDate);
    List<HomeLatestLaunchResponse> latestLaunches = loadLatestLaunches(scope);

    return new HomeDashboardResponse(
        totalReceitas, totalDespesas, saldoFinal, buildMonthlyChart(monthlyRows), latestLaunches);
  }

  private List<HomeTypeTotalRow> loadTypeTotals(DashboardScope scope) {
    if (scope.admin() && scope.roleFiltro() == null) {
      return itemRepository.findTypeTotals();
    }
    return itemRepository.findTypeTotalsByRoleNome(scope.roleFiltro());
  }

  private List<HomeMonthlyBalanceRow> loadMonthlyRows(DashboardScope scope, LocalDate startDate) {
    if (scope.admin() && scope.roleFiltro() == null) {
      return itemRepository.findMonthlyBalanceRowsSince(startDate);
    }
    return itemRepository.findMonthlyBalanceRowsSinceByRoleNome(scope.roleFiltro(), startDate);
  }

  private List<HomeLatestLaunchResponse> loadLatestLaunches(DashboardScope scope) {
    PageRequest limit = PageRequest.of(0, LATEST_LIMIT);
    if (scope.admin() && scope.roleFiltro() == null) {
      return itemRepository.findLatestLaunches(limit);
    }
    return itemRepository.findLatestLaunchesByRoleNome(scope.roleFiltro(), limit);
  }

  private BigDecimal sumByType(List<HomeTypeTotalRow> totals, TipoItem tipo) {
    return totals == null
        ? BigDecimal.ZERO
        : totals.stream()
            .filter(row -> row != null && tipo == row.tipo())
            .map(HomeTypeTotalRow::total)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private List<HomeDashboardMonthResponse> buildMonthlyChart(List<HomeMonthlyBalanceRow> rows) {
    YearMonth currentMonth = YearMonth.now();
    List<YearMonth> months =
        new ArrayList<>(
            java.util.stream.IntStream.range(0, CHART_MONTHS)
                .mapToObj(index -> currentMonth.minusMonths(CHART_MONTHS - 1L - index))
                .toList());

    Map<YearMonth, Totals> totalsByMonth = new LinkedHashMap<>();
    months.forEach(month -> totalsByMonth.put(month, new Totals()));

    if (rows != null) {
      rows.forEach(
          row -> {
            if (row == null) {
              return;
            }
            YearMonth key = YearMonth.of(row.year(), row.month());
            Totals totals = totalsByMonth.get(key);
            if (totals == null || row.total() == null) {
              return;
            }
            if (row.tipo() == TipoItem.RECEITA) {
              totals.income = totals.income.add(row.total());
            } else if (row.tipo() == TipoItem.DESPESA) {
              totals.expense = totals.expense.add(row.total());
            }
          });
    }

    return months.stream()
        .map(
            month ->
                new HomeDashboardMonthResponse(
                    formatMonthLabel(month),
                    totalsByMonth.get(month).income.doubleValue(),
                    totalsByMonth.get(month).expense.doubleValue(),
                    month.equals(currentMonth)))
        .toList();
  }

  private String formatMonthLabel(YearMonth month) {
    String label = month.getMonth().getDisplayName(TextStyle.SHORT, PT_BR).replace(".", "");
    return label.isBlank() ? "" : Character.toUpperCase(label.charAt(0)) + label.substring(1);
  }

  private DashboardScope resolveScope(Authentication authentication, String roleFiltro) {
    boolean admin = isAdmin(authentication);
    String normalizedRole = normalizeRole(roleFiltro);
    if (admin) {
      return new DashboardScope(true, normalizedRole);
    }

    Usuario usuario = buscarUsuarioAutenticado(authentication);
    Set<String> userRoles =
        usuario.getRoles().stream()
            .map(role -> role.getNome())
            .map(this::normalizeRole)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());

    if (userRoles.isEmpty()) {
      return new DashboardScope(false, "__NO_ROLE__");
    }

    if (normalizedRole != null && !userRoles.contains(normalizedRole)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "A role selecionada nao pertence ao usuario autenticado.");
    }

    String resolvedRole = normalizedRole != null ? normalizedRole : userRoles.iterator().next();
    return new DashboardScope(false, resolvedRole);
  }

  private Usuario buscarUsuarioAutenticado(Authentication authentication) {
    if (authentication == null || authentication.getName() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao autenticado");
    }
    return usuarioRepository
        .findByEmail(authentication.getName())
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao encontrado"));
  }

  private boolean isAdmin(Authentication authentication) {
    if (authentication == null) {
      return false;
    }
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
  }

  private String normalizeRole(String role) {
    if (role == null || role.isBlank()) {
      return null;
    }
    return role.trim().toUpperCase(Locale.ROOT);
  }

  private record DashboardScope(boolean admin, String roleFiltro) {}

  private static final class Totals {
    private BigDecimal income = BigDecimal.ZERO;
    private BigDecimal expense = BigDecimal.ZERO;
  }
}
