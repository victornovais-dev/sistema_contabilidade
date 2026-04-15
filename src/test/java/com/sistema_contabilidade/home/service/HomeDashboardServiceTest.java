package com.sistema_contabilidade.home.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.home.dto.HomeDashboardResponse;
import com.sistema_contabilidade.home.dto.HomeLatestLaunchResponse;
import com.sistema_contabilidade.home.dto.HomeMonthlyBalanceRow;
import com.sistema_contabilidade.home.dto.HomeRevenueCategoryTotalRow;
import com.sistema_contabilidade.home.dto.HomeTypeTotalRow;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
@DisplayName("HomeDashboardService unit tests")
class HomeDashboardServiceTest {

  @Mock private ItemRepository itemRepository;
  @Mock private UsuarioRepository usuarioRepository;

  @Test
  @DisplayName("Deve montar dashboard otimizado para admin")
  void deveMontarDashboardOtimizadoParaAdmin() {
    HomeDashboardService service = new HomeDashboardService(itemRepository, usuarioRepository);
    when(itemRepository.findTypeTotals())
        .thenReturn(List.of(new HomeTypeTotalRow(TipoItem.DESPESA, new BigDecimal("40.00"))));
    when(itemRepository.findRevenueCategoryTotals())
        .thenReturn(
            List.of(
                new HomeRevenueCategoryTotalRow("CONTA FEFC", new BigDecimal("70.00")),
                new HomeRevenueCategoryTotalRow("ESTIMAVEL", new BigDecimal("30.00")),
                new HomeRevenueCategoryTotalRow("OUTRAS RECEITAS", new BigDecimal("25.00"))));
    when(itemRepository.findMonthlyBalanceRowsSince(any(LocalDate.class)))
        .thenReturn(
            List.of(
                new HomeMonthlyBalanceRow(2026, 4, TipoItem.RECEITA, new BigDecimal("100.00")),
                new HomeMonthlyBalanceRow(2026, 4, TipoItem.DESPESA, new BigDecimal("40.00"))));
    when(itemRepository.findLatestLaunches(PageRequest.of(0, 4)))
        .thenReturn(
            List.of(
                new HomeLatestLaunchResponse(
                    LocalDateTime.of(2026, 4, 8, 10, 0),
                    LocalDate.of(2026, 4, 8),
                    new BigDecimal("40.00"),
                    TipoItem.DESPESA,
                    "SERVICOS",
                    "EMPRESA TESTE")));

    HomeDashboardResponse dashboard =
        service.getDashboard(autenticacao("admin@email.com", "ADMIN"), null);

    assertEquals(new BigDecimal("70.00"), dashboard.receitasFinanceiras());
    assertEquals(new BigDecimal("30.00"), dashboard.receitasEstimaveis());
    assertEquals(new BigDecimal("100.00"), dashboard.totalReceitas());
    assertEquals(new BigDecimal("40.00"), dashboard.totalDespesas());
    assertEquals(new BigDecimal("60.00"), dashboard.saldoFinal());
    assertEquals(6, dashboard.graficoMensal().size());
    assertEquals(1, dashboard.ultimosLancamentos().size());
    verify(itemRepository).findTypeTotals();
    verify(itemRepository).findRevenueCategoryTotals();
  }

  @Test
  @DisplayName("Deve montar dashboard otimizado filtrado pela role do usuario")
  void deveMontarDashboardOtimizadoFiltradoPelaRoleDoUsuario() {
    HomeDashboardService service = new HomeDashboardService(itemRepository, usuarioRepository);
    when(usuarioRepository.findByEmail("operador@email.com"))
        .thenReturn(Optional.of(usuarioComRole("operador@email.com", "OPERADOR")));
    when(itemRepository.findTypeTotalsByRoleNome("OPERADOR")).thenReturn(List.of());
    when(itemRepository.findRevenueCategoryTotalsByRoleNome("OPERADOR"))
        .thenReturn(
            List.of(
                new HomeRevenueCategoryTotalRow("CONTA DC", new BigDecimal("50.00")),
                new HomeRevenueCategoryTotalRow("ESTIMAVEL", new BigDecimal("30.00")),
                new HomeRevenueCategoryTotalRow("DOACAO", new BigDecimal("20.00"))));
    when(itemRepository.findMonthlyBalanceRowsSinceByRoleNome(eq("OPERADOR"), any(LocalDate.class)))
        .thenReturn(
            List.of(new HomeMonthlyBalanceRow(2026, 4, TipoItem.RECEITA, new BigDecimal("80.00"))));
    when(itemRepository.findLatestLaunchesByRoleNome("OPERADOR", PageRequest.of(0, 4)))
        .thenReturn(List.of());

    HomeDashboardResponse dashboard =
        service.getDashboard(autenticacao("operador@email.com", "OPERADOR"), "operador");

    assertEquals(new BigDecimal("50.00"), dashboard.receitasFinanceiras());
    assertEquals(new BigDecimal("30.00"), dashboard.receitasEstimaveis());
    assertEquals(new BigDecimal("80.00"), dashboard.totalReceitas());
    assertEquals(BigDecimal.ZERO, dashboard.totalDespesas());
    verify(itemRepository).findTypeTotalsByRoleNome("OPERADOR");
    verify(itemRepository).findRevenueCategoryTotalsByRoleNome("OPERADOR");
  }

  private UsernamePasswordAuthenticationToken autenticacao(String email, String... roles) {
    return new UsernamePasswordAuthenticationToken(
        email,
        "n/a",
        java.util.Arrays.stream(roles)
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList());
  }

  private Usuario usuarioComRole(String email, String roleNome) {
    Usuario usuario = new Usuario();
    usuario.setEmail(email);
    usuario.setNome(email);
    usuario.setSenha("123456");
    var role = new com.sistema_contabilidade.rbac.model.Role();
    role.setNome(roleNome);
    usuario.getRoles().add(role);
    return usuario;
  }
}
