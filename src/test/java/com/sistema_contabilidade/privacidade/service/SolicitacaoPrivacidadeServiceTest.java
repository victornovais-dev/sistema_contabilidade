package com.sistema_contabilidade.privacidade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.database.crypto.service.BlindIndexService;
import com.sistema_contabilidade.privacidade.dto.SolicitacaoPrivacidadeConsultaRequest;
import com.sistema_contabilidade.privacidade.dto.SolicitacaoPrivacidadeCreateRequest;
import com.sistema_contabilidade.privacidade.dto.SolicitacaoPrivacidadeUpdateRequest;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidade;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeCanal;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeEscopo;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeStatus;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeTipo;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeVinculo;
import com.sistema_contabilidade.privacidade.repository.SolicitacaoPrivacidadeRepository;
import com.sistema_contabilidade.security.validation.InputSanitizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("SolicitacaoPrivacidadeService unit tests")
class SolicitacaoPrivacidadeServiceTest {

  @Mock private SolicitacaoPrivacidadeRepository solicitacaoRepository;
  @Mock private BlindIndexService blindIndexService;

  private SolicitacaoPrivacidadeService solicitacaoService;

  @BeforeEach
  void setUp() {
    solicitacaoService =
        new SolicitacaoPrivacidadeService(
            solicitacaoRepository, new InputSanitizer(), blindIndexService);
  }

  @Test
  @DisplayName("Deve registrar pedido publico com prazo, identidade pendente e auditoria")
  void deveRegistrarPedidoPublicoComContratoCompleto() {
    when(solicitacaoRepository.existsByProtocolo(anyString())).thenReturn(false);
    when(solicitacaoRepository.save(any(SolicitacaoPrivacidade.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response = solicitacaoService.registrar(requestValido(""));

    ArgumentCaptor<SolicitacaoPrivacidade> captor =
        ArgumentCaptor.forClass(SolicitacaoPrivacidade.class);
    verify(solicitacaoRepository).save(captor.capture());
    SolicitacaoPrivacidade salva = captor.getValue();
    assertEquals(SolicitacaoPrivacidadeStatus.IDENTIDADE_PENDENTE, salva.getStatus());
    assertEquals(salva.getRecebidaEm().plusDays(15), salva.getPrazo());
    assertFalse(salva.isIdentidadeVerificada());
    assertEquals("titular@email.com", salva.getEmailTitular());
    assertEquals(1, salva.getEventos().size());
    assertEquals("Solicitação recebida", salva.getEventos().getFirst().getTitulo());
    assertEquals(salva.getProtocolo(), response.protocolo());
    assertTrue(response.protocolo().matches("LGPD-[0-9]{4}-[A-F0-9]{12}"));
  }

  @Test
  @DisplayName("Honeypot preenchido deve simular sucesso sem persistir dados")
  void deveDescartarSubmissaoDoHoneypotSemAlertarBot() {
    when(solicitacaoRepository.existsByProtocolo(anyString())).thenReturn(false);

    var response = solicitacaoService.registrar(requestValido("https://spam.invalid"));

    assertTrue(response.protocolo().startsWith("LGPD-"));
    verify(solicitacaoRepository, never()).save(any());
  }

  @Test
  @DisplayName("Consulta deve usar protocolo e indice cego do email normalizado")
  void deveConsultarComIndiceCegoSemBuscaPorEmailCifrado() {
    SolicitacaoPrivacidade solicitacao = solicitacaoEmAnalise();
    when(blindIndexService.email("titular@email.com")).thenReturn("blind-index");
    when(solicitacaoRepository.findByProtocoloAndEmailBlindIndex(
            "LGPD-2026-A1B2C3D4E5F6", "blind-index"))
        .thenReturn(Optional.of(solicitacao));

    var response =
        solicitacaoService.consultar(
            new SolicitacaoPrivacidadeConsultaRequest(
                "LGPD-2026-A1B2C3D4E5F6", " TITULAR@EMAIL.COM "));

    assertEquals(SolicitacaoPrivacidadeStatus.EM_ANALISE, response.status());
    assertEquals(SolicitacaoPrivacidadeTipo.ACESSO, response.tipo());
  }

  @Test
  @DisplayName("Deve aplicar retencao legal somente com motivo e registrar auditoria")
  void deveAplicarRetencaoLegalComMotivo() {
    SolicitacaoPrivacidade solicitacao = solicitacaoEmAnalise();
    when(solicitacaoRepository.findWithEventosByProtocolo(solicitacao.getProtocolo()))
        .thenReturn(Optional.of(solicitacao));
    when(solicitacaoRepository.save(solicitacao)).thenReturn(solicitacao);

    var response =
        solicitacaoService.atualizar(
            solicitacao.getProtocolo(),
            new SolicitacaoPrivacidadeUpdateRequest(
                null, null, true, "Obrigação regulatória vigente.", null, null),
            "admin@email.com");

    assertTrue(response.retencaoLegal());
    assertEquals(SolicitacaoPrivacidadeStatus.RETENCAO_LEGAL, response.status());
    assertEquals("Obrigação regulatória vigente.", response.motivoRetencao());
    assertEquals("Retenção legal aplicada", response.eventos().getFirst().titulo());
  }

  @Test
  @DisplayName("Deve bloquear reabertura de solicitacao concluida")
  void deveBloquearTransicaoInvalidaDeStatus() {
    SolicitacaoPrivacidade solicitacao = solicitacaoEmAnalise();
    solicitacao.setStatus(SolicitacaoPrivacidadeStatus.CONCLUIDA);
    when(solicitacaoRepository.findWithEventosByProtocolo(solicitacao.getProtocolo()))
        .thenReturn(Optional.of(solicitacao));
    String protocolo = solicitacao.getProtocolo();
    SolicitacaoPrivacidadeUpdateRequest request =
        new SolicitacaoPrivacidadeUpdateRequest(
            SolicitacaoPrivacidadeStatus.EM_ANALISE, null, null, null, null, null);

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> solicitacaoService.atualizar(protocolo, request, "admin@email.com"));

    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    verify(solicitacaoRepository, never()).save(any());
  }

  private SolicitacaoPrivacidadeCreateRequest requestValido(String website) {
    return new SolicitacaoPrivacidadeCreateRequest(
        " Titular Teste ",
        " TITULAR@EMAIL.COM ",
        " Campanha Teste ",
        SolicitacaoPrivacidadeVinculo.USUARIO,
        SolicitacaoPrivacidadeTipo.ACESSO,
        Set.of(SolicitacaoPrivacidadeEscopo.CADASTRO_PERFIL),
        SolicitacaoPrivacidadeCanal.EMAIL,
        null,
        "Quero acessar os dados associados ao meu cadastro.",
        true,
        website);
  }

  private SolicitacaoPrivacidade solicitacaoEmAnalise() {
    LocalDate hoje = LocalDate.of(2026, Month.JULY, 30);
    LocalDateTime agora = hoje.atTime(10, 0);
    SolicitacaoPrivacidade solicitacao = new SolicitacaoPrivacidade();
    solicitacao.setProtocolo("LGPD-2026-A1B2C3D4E5F6");
    solicitacao.setNomeTitular("Titular Teste");
    solicitacao.setEmailTitular("titular@email.com");
    solicitacao.setOrganizacao("Campanha Teste");
    solicitacao.setVinculo(SolicitacaoPrivacidadeVinculo.USUARIO);
    solicitacao.setTipo(SolicitacaoPrivacidadeTipo.ACESSO);
    solicitacao.setEscopos("CADASTRO_PERFIL");
    solicitacao.setCanalResposta(SolicitacaoPrivacidadeCanal.EMAIL);
    solicitacao.setStatus(SolicitacaoPrivacidadeStatus.EM_ANALISE);
    solicitacao.setRecebidaEm(hoje);
    solicitacao.setPrazo(hoje.plusDays(15));
    solicitacao.setResponsavel("Equipe de Privacidade");
    solicitacao.setIdentidadeVerificada(true);
    solicitacao.setDescricao("Quero acessar os dados associados ao meu cadastro.");
    solicitacao.setVersaoAviso("2026-07");
    solicitacao.setCriadaEm(agora);
    solicitacao.setAtualizadaEm(agora);
    return solicitacao;
  }
}
