package com.sistema_contabilidade.notificacao.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sistema_contabilidade.notificacao.dto.NotificacaoListResponse;
import com.sistema_contabilidade.notificacao.service.NotificacaoService;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificacaoController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("NotificacaoController WebMvc tests")
class NotificacaoControllerWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private NotificacaoService notificacaoService;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private CustomUserDetailsService customUserDetailsService;

  @Test
  @DisplayName("Deve retornar lista de notificacoes")
  void listarDeveRetornarNotificacoes() throws Exception {
    when(notificacaoService.listar(any(), eq(null)))
        .thenReturn(
            List.of(
                new NotificacaoListResponse(
                    UUID.fromString("11111111-2222-3333-4444-555555555555"),
                    UUID.fromString("99999999-2222-3333-4444-555555555555"),
                    "MANAGER",
                    "CONTA DC",
                    "GOV SP",
                    new BigDecimal("100000.00"),
                    LocalDateTime.of(2026, 4, 14, 10, 45))));

    mockMvc
        .perform(get("/api/v1/notificacoes"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].role").value("MANAGER"))
        .andExpect(jsonPath("$[0].descricao").value("CONTA DC"))
        .andExpect(jsonPath("$[0].valor").value(100000.00));
  }

  @Test
  @DisplayName("Deve retornar roles disponiveis para filtro")
  void listarRolesDisponiveisDeveRetornarOk() throws Exception {
    when(notificacaoService.listarRolesDisponiveis(any()))
        .thenReturn(List.of("ADMIN", "MANAGER", "SUPPORT"));

    mockMvc
        .perform(get("/api/v1/notificacoes/roles"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value("ADMIN"))
        .andExpect(jsonPath("$[1]").value("MANAGER"))
        .andExpect(jsonPath("$[2]").value("SUPPORT"));
  }
}
