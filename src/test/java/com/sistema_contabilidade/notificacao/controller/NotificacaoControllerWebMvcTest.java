package com.sistema_contabilidade.notificacao.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sistema_contabilidade.auth.service.SessaoUsuarioService;
import com.sistema_contabilidade.notificacao.dto.NotificacaoListResponse;
import com.sistema_contabilidade.notificacao.service.NotificacaoService;
import com.sistema_contabilidade.security.service.AdminRouteService;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import com.sistema_contabilidade.security.service.RequestFingerprintService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificacaoController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("NotificacaoController WebMvc tests")
class NotificacaoControllerWebMvcTest {

  private static final String NOTIFICATION_ACCESS_EXPRESSION = "hasAnyRole('ADMIN','CONTABIL')";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private NotificacaoService notificacaoService;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private CustomUserDetailsService customUserDetailsService;
  @MockitoBean private AdminRouteService adminRouteService;
  @MockitoBean private RequestFingerprintService requestFingerprintService;
  @MockitoBean private SessaoUsuarioService sessaoUsuarioService;

  @Test
  @DisplayName("Deve restringir controller para admin e contabil")
  void controllerDeveExigirAdminOuContabil() {
    PreAuthorize preAuthorize = NotificacaoController.class.getAnnotation(PreAuthorize.class);

    assertNotNull(preAuthorize);
    assertEquals(NOTIFICATION_ACCESS_EXPRESSION, preAuthorize.value());
  }

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
                    LocalDateTime.of(2026, 4, 14, 10, 45),
                    false)));

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
    when(notificacaoService.listarRolesDisponiveis(any())).thenReturn(List.of("OPERATOR"));

    mockMvc
        .perform(get("/api/v1/notificacoes/roles"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value("OPERATOR"));
  }

  @Test
  @DisplayName("Deve atualizar limpeza da notificacao")
  void atualizarLimpezaDeveRetornarOk() throws Exception {
    UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
    when(notificacaoService.atualizarLimpeza(any(), eq(id), eq(true)))
        .thenReturn(
            new NotificacaoListResponse(
                id,
                UUID.fromString("99999999-2222-3333-4444-555555555555"),
                "MANAGER",
                "CONTA DC",
                "GOV SP",
                new BigDecimal("100000.00"),
                LocalDateTime.of(2026, 4, 14, 10, 45),
                true));

    mockMvc
        .perform(
            patch("/api/v1/notificacoes/{id}/limpeza", id)
                .contentType("application/json")
                .content(
                    """
                    {"limpa":true}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.limpa").value(true));
  }
}
