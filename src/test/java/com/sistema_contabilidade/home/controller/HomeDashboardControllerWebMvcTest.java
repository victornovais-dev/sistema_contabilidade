package com.sistema_contabilidade.home.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sistema_contabilidade.home.dto.HomeDashboardMonthResponse;
import com.sistema_contabilidade.home.dto.HomeDashboardResponse;
import com.sistema_contabilidade.home.dto.HomeLatestLaunchResponse;
import com.sistema_contabilidade.home.service.HomeDashboardService;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HomeDashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("HomeDashboardController WebMvc tests")
class HomeDashboardControllerWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private HomeDashboardService homeDashboardService;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private CustomUserDetailsService customUserDetailsService;

  @Test
  @DisplayName("Deve retornar dashboard da home")
  void deveRetornarDashboardDaHome() throws Exception {
    when(homeDashboardService.getDashboard(any(), eq("OPERADOR")))
        .thenReturn(
            new HomeDashboardResponse(
                new BigDecimal("100.00"),
                new BigDecimal("40.00"),
                new BigDecimal("60.00"),
                List.of(new HomeDashboardMonthResponse("Abr", 100.0d, 40.0d, true)),
                List.of(
                    new HomeLatestLaunchResponse(
                        LocalDateTime.of(2026, 4, 8, 10, 0),
                        LocalDate.of(2026, 4, 8),
                        new BigDecimal("40.00"),
                        TipoItem.DESPESA,
                        "SERVICOS",
                        "EMPRESA TESTE"))));

    mockMvc
        .perform(get("/api/v1/home/dashboard").param("role", "OPERADOR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalReceitas").value(100.00))
        .andExpect(jsonPath("$.totalDespesas").value(40.00))
        .andExpect(jsonPath("$.graficoMensal[0].label").value("Abr"))
        .andExpect(jsonPath("$.ultimosLancamentos[0].razaoSocialNome").value("EMPRESA TESTE"));
  }
}
