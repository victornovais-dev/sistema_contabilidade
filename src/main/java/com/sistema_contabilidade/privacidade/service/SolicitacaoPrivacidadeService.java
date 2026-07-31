package com.sistema_contabilidade.privacidade.service;

import com.sistema_contabilidade.database.crypto.service.BlindIndexService;
import com.sistema_contabilidade.privacidade.dto.SolicitacaoPrivacidadeConsultaRequest;
import com.sistema_contabilidade.privacidade.dto.SolicitacaoPrivacidadeConsultaResponse;
import com.sistema_contabilidade.privacidade.dto.SolicitacaoPrivacidadeCreateRequest;
import com.sistema_contabilidade.privacidade.dto.SolicitacaoPrivacidadeCreateResponse;
import com.sistema_contabilidade.privacidade.dto.SolicitacaoPrivacidadeEventoResponse;
import com.sistema_contabilidade.privacidade.dto.SolicitacaoPrivacidadeListResponse;
import com.sistema_contabilidade.privacidade.dto.SolicitacaoPrivacidadeResponse;
import com.sistema_contabilidade.privacidade.dto.SolicitacaoPrivacidadeResumoResponse;
import com.sistema_contabilidade.privacidade.dto.SolicitacaoPrivacidadeUpdateRequest;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidade;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeEscopo;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeEvento;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeStatus;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeTipo;
import com.sistema_contabilidade.privacidade.repository.SolicitacaoPrivacidadeRepository;
import com.sistema_contabilidade.security.validation.InputSanitizer;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SolicitacaoPrivacidadeService {

  private static final int PRAZO_ATENDIMENTO_DIAS = 15;
  private static final int PROTOCOLO_TENTATIVAS = 10;
  private static final String RESPONSAVEL_PADRAO = "Equipe de Privacidade";
  private static final String ATOR_PORTAL = "Portal público";
  private static final String VERSAO_AVISO = "2026-07";
  private static final Set<SolicitacaoPrivacidadeStatus> ENCERRADOS =
      EnumSet.of(
          SolicitacaoPrivacidadeStatus.CONCLUIDA,
          SolicitacaoPrivacidadeStatus.PARCIALMENTE_ATENDIDA,
          SolicitacaoPrivacidadeStatus.NEGADA,
          SolicitacaoPrivacidadeStatus.CANCELADA);
  private static final Map<SolicitacaoPrivacidadeStatus, Set<SolicitacaoPrivacidadeStatus>>
      TRANSICOES = criarTransicoes();

  private final SolicitacaoPrivacidadeRepository solicitacaoRepository;
  private final InputSanitizer inputSanitizer;
  private final BlindIndexService blindIndexService;
  private final SecureRandom secureRandom = new SecureRandom();

  @Transactional
  public SolicitacaoPrivacidadeCreateResponse registrar(
      SolicitacaoPrivacidadeCreateRequest request) {
    LocalDate hoje = LocalDate.now(ZoneId.systemDefault());
    if (request.website() != null && !request.website().isBlank()) {
      return new SolicitacaoPrivacidadeCreateResponse(gerarProtocolo(), hoje, hoje.plusDays(15));
    }

    LocalDateTime agora = LocalDateTime.now(ZoneId.systemDefault());
    SolicitacaoPrivacidade solicitacao = new SolicitacaoPrivacidade();
    solicitacao.setProtocolo(gerarProtocolo());
    solicitacao.setNomeTitular(inputSanitizer.sanitizeInlineText(request.nome(), "nome", 120));
    solicitacao.setEmailTitular(inputSanitizer.sanitizeEmail(request.email(), "email"));
    solicitacao.setOrganizacao(
        inputSanitizer.sanitizeInlineText(request.organizacao(), "organizacao", 120));
    solicitacao.setVinculo(request.vinculo());
    solicitacao.setTipo(request.tipo());
    solicitacao.setEscopos(serializarEscopos(request.escopos()));
    solicitacao.setCanalResposta(request.canalResposta());
    solicitacao.setReferenciaTitular(
        inputSanitizer.sanitizeInlineText(request.referencia(), "referencia", 100));
    solicitacao.setStatus(SolicitacaoPrivacidadeStatus.IDENTIDADE_PENDENTE);
    solicitacao.setRecebidaEm(hoje);
    solicitacao.setPrazo(hoje.plusDays(PRAZO_ATENDIMENTO_DIAS));
    solicitacao.setResponsavel(RESPONSAVEL_PADRAO);
    solicitacao.setIdentidadeVerificada(false);
    solicitacao.setDescricao(
        inputSanitizer.sanitizeMultilineText(request.descricao(), "descricao", 1600));
    solicitacao.setRetencaoLegal(false);
    solicitacao.setVersaoAviso(VERSAO_AVISO);
    solicitacao.setCriadaEm(agora);
    solicitacao.setAtualizadaEm(agora);
    adicionarEvento(
        solicitacao,
        "Solicitação recebida",
        "Pedido enviado pelo canal público de privacidade.",
        ATOR_PORTAL,
        agora);

    SolicitacaoPrivacidade salva = solicitacaoRepository.save(solicitacao);
    return new SolicitacaoPrivacidadeCreateResponse(
        salva.getProtocolo(), salva.getRecebidaEm(), salva.getPrazo());
  }

  @Transactional(readOnly = true)
  public SolicitacaoPrivacidadeConsultaResponse consultar(
      SolicitacaoPrivacidadeConsultaRequest request) {
    String protocolo = request.protocolo().strip().toUpperCase(Locale.ROOT);
    String email = inputSanitizer.sanitizeEmail(request.email(), "email");
    SolicitacaoPrivacidade solicitacao =
        solicitacaoRepository
            .findByProtocoloAndEmailBlindIndex(protocolo, blindIndexService.email(email))
            .orElseThrow(this::solicitacaoNaoEncontrada);
    return new SolicitacaoPrivacidadeConsultaResponse(
        solicitacao.getProtocolo(),
        solicitacao.getTipo(),
        solicitacao.getStatus(),
        solicitacao.getRecebidaEm(),
        solicitacao.getAtualizadaEm());
  }

  @Transactional(readOnly = true)
  public SolicitacaoPrivacidadeListResponse listar(
      String termo,
      SolicitacaoPrivacidadeStatus status,
      SolicitacaoPrivacidadeTipo tipo,
      int pagina,
      int tamanho) {
    int paginaSegura = Math.max(pagina, 0);
    int tamanhoSeguro = Math.clamp(tamanho, 1, 50);
    PageRequest pageable =
        PageRequest.of(
            paginaSegura, tamanhoSeguro, Sort.by(Sort.Direction.DESC, "recebidaEm", "criadaEm"));
    Page<SolicitacaoPrivacidade> resultado =
        solicitacaoRepository.buscar(normalizarTermo(termo), status, tipo, pageable);
    return new SolicitacaoPrivacidadeListResponse(
        resultado.getContent().stream().map(this::toResponseSemEventos).toList(),
        resultado.getNumber(),
        resultado.getSize(),
        resultado.getTotalElements(),
        resultado.getTotalPages(),
        criarResumo());
  }

  @Transactional(readOnly = true)
  public SolicitacaoPrivacidadeResponse detalhar(String protocolo) {
    return toResponse(
        solicitacaoRepository
            .findWithEventosByProtocolo(protocolo.strip().toUpperCase(Locale.ROOT))
            .orElseThrow(this::solicitacaoNaoEncontrada));
  }

  @Transactional
  public SolicitacaoPrivacidadeResponse atualizar(
      String protocolo, SolicitacaoPrivacidadeUpdateRequest request, String ator) {
    SolicitacaoPrivacidade solicitacao =
        solicitacaoRepository
            .findWithEventosByProtocolo(protocolo.strip().toUpperCase(Locale.ROOT))
            .orElseThrow(this::solicitacaoNaoEncontrada);
    LocalDateTime agora = LocalDateTime.now(ZoneId.systemDefault());
    String atorSeguro = inputSanitizer.sanitizeInlineText(ator, "ator", 160);

    atualizarResponsavel(solicitacao, request, atorSeguro, agora);
    atualizarIdentidade(solicitacao, request, atorSeguro, agora);
    atualizarRetencaoLegal(solicitacao, request, atorSeguro, agora);
    atualizarStatus(solicitacao, request, atorSeguro, agora);
    adicionarObservacao(solicitacao, request, atorSeguro, agora);
    solicitacao.setAtualizadaEm(agora);
    return toResponse(solicitacaoRepository.save(solicitacao));
  }

  private void atualizarResponsavel(
      SolicitacaoPrivacidade solicitacao,
      SolicitacaoPrivacidadeUpdateRequest request,
      String ator,
      LocalDateTime agora) {
    if (request.responsavel() == null) {
      return;
    }
    String responsavel =
        inputSanitizer.sanitizeInlineText(request.responsavel(), "responsavel", 100);
    if (responsavel == null
        || responsavel.isBlank()
        || responsavel.equals(solicitacao.getResponsavel())) {
      return;
    }
    solicitacao.setResponsavel(responsavel);
    adicionarEvento(
        solicitacao,
        "Responsável alterado",
        "Atendimento atribuído a " + responsavel + ".",
        ator,
        agora);
  }

  private void atualizarIdentidade(
      SolicitacaoPrivacidade solicitacao,
      SolicitacaoPrivacidadeUpdateRequest request,
      String ator,
      LocalDateTime agora) {
    boolean identidadeVerificada = Boolean.TRUE.equals(request.identidadeVerificada());
    if (request.identidadeVerificada() == null
        || identidadeVerificada == solicitacao.isIdentidadeVerificada()) {
      return;
    }
    solicitacao.setIdentidadeVerificada(identidadeVerificada);
    adicionarEvento(
        solicitacao,
        identidadeVerificada ? "Identidade verificada" : "Validação de identidade reaberta",
        identidadeVerificada
            ? "A identidade do titular foi confirmada."
            : "Nova confirmação de identidade será necessária.",
        ator,
        agora);
  }

  private void atualizarRetencaoLegal(
      SolicitacaoPrivacidade solicitacao,
      SolicitacaoPrivacidadeUpdateRequest request,
      String ator,
      LocalDateTime agora) {
    boolean retencaoLegal = Boolean.TRUE.equals(request.retencaoLegal());
    if (request.retencaoLegal() == null || retencaoLegal == solicitacao.isRetencaoLegal()) {
      return;
    }
    if (retencaoLegal) {
      String motivo =
          inputSanitizer.sanitizeMultilineText(request.motivoRetencao(), "motivoRetencao", 1000);
      if (motivo == null || motivo.isBlank()) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Motivo da retencao legal e obrigatorio");
      }
      validarTransicao(solicitacao.getStatus(), SolicitacaoPrivacidadeStatus.RETENCAO_LEGAL);
      solicitacao.setRetencaoLegal(true);
      solicitacao.setMotivoRetencao(motivo);
      solicitacao.setStatus(SolicitacaoPrivacidadeStatus.RETENCAO_LEGAL);
      adicionarEvento(solicitacao, "Retenção legal aplicada", motivo, ator, agora);
      return;
    }
    solicitacao.setRetencaoLegal(false);
    solicitacao.setMotivoRetencao(null);
    if (solicitacao.getStatus() == SolicitacaoPrivacidadeStatus.RETENCAO_LEGAL) {
      solicitacao.setStatus(SolicitacaoPrivacidadeStatus.EM_ANALISE);
    }
    adicionarEvento(
        solicitacao,
        "Retenção legal liberada",
        "A retenção excepcional foi encerrada e o pedido voltou para análise.",
        ator,
        agora);
  }

  private void atualizarStatus(
      SolicitacaoPrivacidade solicitacao,
      SolicitacaoPrivacidadeUpdateRequest request,
      String ator,
      LocalDateTime agora) {
    SolicitacaoPrivacidadeStatus novoStatus = request.status();
    if (novoStatus == null || novoStatus == solicitacao.getStatus()) {
      return;
    }
    validarTransicao(solicitacao.getStatus(), novoStatus);
    if (novoStatus == SolicitacaoPrivacidadeStatus.RETENCAO_LEGAL
        && !solicitacao.isRetencaoLegal()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Aplique a retencao legal e informe o motivo");
    }
    solicitacao.setStatus(novoStatus);
    adicionarEvento(
        solicitacao,
        "Status atualizado",
        "Solicitação alterada para " + novoStatus.name() + ".",
        ator,
        agora);
  }

  private void adicionarObservacao(
      SolicitacaoPrivacidade solicitacao,
      SolicitacaoPrivacidadeUpdateRequest request,
      String ator,
      LocalDateTime agora) {
    String observacao =
        inputSanitizer.sanitizeMultilineText(request.observacao(), "observacao", 1000);
    if (observacao != null && !observacao.isBlank()) {
      adicionarEvento(solicitacao, "Observação interna", observacao, ator, agora);
    }
  }

  private void validarTransicao(
      SolicitacaoPrivacidadeStatus atual, SolicitacaoPrivacidadeStatus proximo) {
    if (!TRANSICOES.getOrDefault(atual, Set.of()).contains(proximo)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Transicao de status nao permitida");
    }
  }

  private SolicitacaoPrivacidadeResumoResponse criarResumo() {
    LocalDate hoje = LocalDate.now(ZoneId.systemDefault());
    return new SolicitacaoPrivacidadeResumoResponse(
        solicitacaoRepository.countByStatusNotIn(ENCERRADOS),
        solicitacaoRepository.countByPrazoBetweenAndStatusNotIn(hoje, hoje.plusDays(3), ENCERRADOS),
        solicitacaoRepository.countByPrazoBeforeAndStatusNotIn(hoje, ENCERRADOS),
        solicitacaoRepository.countByStatus(SolicitacaoPrivacidadeStatus.CONCLUIDA));
  }

  private SolicitacaoPrivacidadeResponse toResponseSemEventos(SolicitacaoPrivacidade solicitacao) {
    return toResponse(solicitacao, false);
  }

  private SolicitacaoPrivacidadeResponse toResponse(SolicitacaoPrivacidade solicitacao) {
    return toResponse(solicitacao, true);
  }

  private SolicitacaoPrivacidadeResponse toResponse(
      SolicitacaoPrivacidade solicitacao, boolean incluirEventos) {
    return new SolicitacaoPrivacidadeResponse(
        solicitacao.getProtocolo(),
        solicitacao.getNomeTitular(),
        solicitacao.getEmailTitular(),
        solicitacao.getOrganizacao(),
        solicitacao.getVinculo(),
        solicitacao.getTipo(),
        desserializarEscopos(solicitacao.getEscopos()),
        solicitacao.getCanalResposta(),
        solicitacao.getReferenciaTitular(),
        solicitacao.getStatus(),
        solicitacao.getRecebidaEm(),
        solicitacao.getPrazo(),
        solicitacao.getResponsavel(),
        solicitacao.isIdentidadeVerificada(),
        solicitacao.getDescricao(),
        solicitacao.isRetencaoLegal(),
        solicitacao.getMotivoRetencao(),
        solicitacao.getCriadaEm(),
        solicitacao.getAtualizadaEm(),
        incluirEventos
            ? solicitacao.getEventos().stream()
                .map(
                    evento ->
                        new SolicitacaoPrivacidadeEventoResponse(
                            evento.getTitulo(),
                            evento.getDescricao(),
                            evento.getAtor(),
                            evento.getOcorridoEm()))
                .toList()
            : java.util.List.of());
  }

  private void adicionarEvento(
      SolicitacaoPrivacidade solicitacao,
      String titulo,
      String descricao,
      String ator,
      LocalDateTime ocorridoEm) {
    SolicitacaoPrivacidadeEvento evento = new SolicitacaoPrivacidadeEvento();
    evento.setTitulo(titulo);
    evento.setDescricao(descricao);
    evento.setAtor(ator);
    evento.setOcorridoEm(ocorridoEm);
    solicitacao.adicionarEvento(evento);
  }

  private String gerarProtocolo() {
    byte[] bytes = new byte[6];
    int ano = LocalDate.now(ZoneId.systemDefault()).getYear();
    for (int tentativa = 0; tentativa < PROTOCOLO_TENTATIVAS; tentativa++) {
      secureRandom.nextBytes(bytes);
      String protocolo = "LGPD-" + ano + "-" + HexFormat.of().withUpperCase().formatHex(bytes);
      if (!solicitacaoRepository.existsByProtocolo(protocolo)) {
        return protocolo;
      }
    }
    throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE, "Nao foi possivel gerar protocolo");
  }

  private String serializarEscopos(Set<SolicitacaoPrivacidadeEscopo> escopos) {
    return escopos.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
  }

  private Set<SolicitacaoPrivacidadeEscopo> desserializarEscopos(String escopos) {
    if (escopos == null || escopos.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(escopos.split(","))
        .map(SolicitacaoPrivacidadeEscopo::valueOf)
        .collect(Collectors.toUnmodifiableSet());
  }

  private String normalizarTermo(String termo) {
    if (termo == null) {
      return "";
    }
    String normalizado = termo.strip().toLowerCase(Locale.ROOT);
    return normalizado.substring(0, Math.min(normalizado.length(), 32));
  }

  private ResponseStatusException solicitacaoNaoEncontrada() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitacao nao encontrada");
  }

  private static Map<SolicitacaoPrivacidadeStatus, Set<SolicitacaoPrivacidadeStatus>>
      criarTransicoes() {
    Map<SolicitacaoPrivacidadeStatus, Set<SolicitacaoPrivacidadeStatus>> transicoes =
        new EnumMap<>(SolicitacaoPrivacidadeStatus.class);
    transicoes.put(
        SolicitacaoPrivacidadeStatus.RECEBIDA,
        EnumSet.of(
            SolicitacaoPrivacidadeStatus.IDENTIDADE_PENDENTE,
            SolicitacaoPrivacidadeStatus.EM_ANALISE,
            SolicitacaoPrivacidadeStatus.CANCELADA));
    transicoes.put(
        SolicitacaoPrivacidadeStatus.IDENTIDADE_PENDENTE,
        EnumSet.of(
            SolicitacaoPrivacidadeStatus.EM_ANALISE, SolicitacaoPrivacidadeStatus.CANCELADA));
    transicoes.put(
        SolicitacaoPrivacidadeStatus.EM_ANALISE,
        EnumSet.of(
            SolicitacaoPrivacidadeStatus.RETENCAO_LEGAL,
            SolicitacaoPrivacidadeStatus.PRONTA_EXECUCAO,
            SolicitacaoPrivacidadeStatus.NEGADA,
            SolicitacaoPrivacidadeStatus.PARCIALMENTE_ATENDIDA,
            SolicitacaoPrivacidadeStatus.CANCELADA));
    transicoes.put(
        SolicitacaoPrivacidadeStatus.RETENCAO_LEGAL,
        EnumSet.of(
            SolicitacaoPrivacidadeStatus.EM_ANALISE,
            SolicitacaoPrivacidadeStatus.PRONTA_EXECUCAO,
            SolicitacaoPrivacidadeStatus.NEGADA));
    transicoes.put(
        SolicitacaoPrivacidadeStatus.PRONTA_EXECUCAO,
        EnumSet.of(
            SolicitacaoPrivacidadeStatus.CONCLUIDA,
            SolicitacaoPrivacidadeStatus.PARCIALMENTE_ATENDIDA,
            SolicitacaoPrivacidadeStatus.RETENCAO_LEGAL));
    transicoes.put(
        SolicitacaoPrivacidadeStatus.PARCIALMENTE_ATENDIDA,
        EnumSet.of(
            SolicitacaoPrivacidadeStatus.CONCLUIDA, SolicitacaoPrivacidadeStatus.RETENCAO_LEGAL));
    return Map.copyOf(transicoes);
  }
}
