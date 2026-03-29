package com.sistema_contabilidade.relatorio.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResponse;
import com.sistema_contabilidade.relatorio.service.RelatorioFinanceiroService;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RelatorioController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("RelatorioController WebMvc tests")
class RelatorioControllerWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RelatorioFinanceiroService relatorioFinanceiroService;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private CustomUserDetailsService customUserDetailsService;

  @Test
  @DisplayName("Deve retornar relatorio financeiro")
  void obterRelatorioFinanceiroDeveRetornarOk() throws Exception {
    RelatorioFinanceiroResponse response =
        new RelatorioFinanceiroResponse(
            new BigDecimal("1500.00"),
            new BigDecimal("500.00"),
            new BigDecimal("1000.00"),
            List.of(),
            List.of());
    when(relatorioFinanceiroService.gerar(any(), eq(null))).thenReturn(response);

    mockMvc
        .perform(get("/api/v1/relatorios/financeiro"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalReceitas").value(1500.00))
        .andExpect(jsonPath("$.totalDespesas").value(500.00))
        .andExpect(jsonPath("$.saldoFinal").value(1000.00));
  }

  @Test
  @DisplayName("Deve baixar relatorio financeiro em PDF")
  void baixarRelatorioFinanceiroPdfDeveRetornarPdf() throws Exception {
    RelatorioFinanceiroResponse relatorio =
        new RelatorioFinanceiroResponse(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of());
    byte[] pdf = "%PDF-1.4 teste".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    when(relatorioFinanceiroService.gerar(any(), eq(null))).thenReturn(relatorio);
    when(relatorioFinanceiroService.gerarPdf(relatorio)).thenReturn(pdf);

    mockMvc
        .perform(get("/api/v1/relatorios/financeiro/pdf"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(
            header()
                .string("Content-Disposition", "attachment; filename=\"relatorio-financeiro.pdf\""))
        .andExpect(content().bytes(pdf));
  }

  @Test
  @DisplayName("Deve retornar roles disponiveis para filtro")
  void listarRolesDisponiveisDeveRetornarOk() throws Exception {
    when(relatorioFinanceiroService.listarRolesDisponiveis(any()))
        .thenReturn(List.of("ADMIN", "MANAGER", "OPERATOR"));

    mockMvc
        .perform(get("/api/v1/relatorios/roles"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value("ADMIN"))
        .andExpect(jsonPath("$[1]").value("MANAGER"))
        .andExpect(jsonPath("$[2]").value("OPERATOR"));
  }
}
