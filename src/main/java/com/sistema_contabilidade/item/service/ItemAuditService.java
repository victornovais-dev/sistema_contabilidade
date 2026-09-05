package com.sistema_contabilidade.item.service;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.ItemArquivo;
import com.sistema_contabilidade.item.model.ItemAuditoria;
import com.sistema_contabilidade.item.model.ItemAuditoriaAcao;
import com.sistema_contabilidade.item.model.ItemParcelaPagamento;
import com.sistema_contabilidade.item.model.ItemParcelaPagamentoArquivo;
import com.sistema_contabilidade.item.repository.ItemAuditoriaRepository;
import com.sistema_contabilidade.security.service.RequestFingerprintService;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ItemAuditService {

  private static final String TRACE_ID_MDC_KEY = "trace.id";
  private static final int TRACE_ID_LENGTH = 32;
  private static final int MAX_IP_LENGTH = 64;
  private static final int MAX_USER_AGENT_LENGTH = 1024;

  private final ItemAuditoriaRepository auditoriaRepository;
  private final UsuarioRepository usuarioRepository;
  private final RequestFingerprintService requestFingerprintService;
  private final ArquivoStorageService arquivoStorageService;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  @Autowired
  public ItemAuditService(
      ItemAuditoriaRepository auditoriaRepository,
      UsuarioRepository usuarioRepository,
      RequestFingerprintService requestFingerprintService,
      ArquivoStorageService arquivoStorageService,
      ObjectMapper objectMapper) {
    this(
        auditoriaRepository,
        usuarioRepository,
        requestFingerprintService,
        arquivoStorageService,
        objectMapper,
        Clock.systemUTC());
  }

  ItemAuditService(
      ItemAuditoriaRepository auditoriaRepository,
      UsuarioRepository usuarioRepository,
      RequestFingerprintService requestFingerprintService,
      ArquivoStorageService arquivoStorageService,
      ObjectMapper objectMapper,
      Clock clock) {
    this.auditoriaRepository = Objects.requireNonNull(auditoriaRepository, "auditoriaRepository");
    this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "usuarioRepository");
    this.requestFingerprintService =
        Objects.requireNonNull(requestFingerprintService, "requestFingerprintService");
    this.arquivoStorageService =
        Objects.requireNonNull(arquivoStorageService, "arquivoStorageService");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public ItemAuditSnapshot capturarSnapshot(Item item) {
    Objects.requireNonNull(item, "item");
    UUID itemId = Objects.requireNonNull(item.getId(), "item.id");
    Map<String, Object> campos = new LinkedHashMap<>();
    campos.put("id", itemId);
    campos.put("valor", normalizarValor(item.getValor()));
    campos.put("data", valorTexto(item.getData()));
    campos.put("horarioCriacao", valorTexto(item.getHorarioCriacao()));
    campos.put("tipo", valorTexto(item.getTipo()));
    campos.put("role", item.getRoleNome());
    campos.put("descricao", item.getDescricao());
    campos.put("tipoDocumento", item.getTipoDocumento());
    campos.put("numeroDocumento", item.getNumeroDocumento());
    campos.put("razaoSocialNome", item.getRazaoSocialNome());
    campos.put("cnpjCpf", item.getCnpjCpf());
    campos.put("observacao", item.getObservacao());
    campos.put("verificado", item.isVerificado());
    campos.put("criadoPorId", item.getCriadoPor() == null ? null : item.getCriadoPor().getId());
    campos.put("arquivos", snapshotArquivosItem(item));
    campos.put("pagamento", snapshotPagamento(item));
    return new ItemAuditSnapshot(itemId, campos);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<ItemAuditoria> registrar(
      ItemAuditoriaAcao acao,
      ItemAuditSnapshot antes,
      ItemAuditSnapshot depois,
      Authentication authentication,
      HttpServletRequest request) {
    Objects.requireNonNull(acao, "acao");
    Objects.requireNonNull(request, "request");
    validarSnapshots(acao, antes, depois);

    Map<String, ValorAlterado> alteracoes = calcularAlteracoes(antes, depois);
    if (!acaoLifecycle(acao) && alteracoes.isEmpty()) {
      return Optional.empty();
    }

    ItemAuditSnapshot snapshotEvento =
        switch (acao) {
          case CRIADO -> depois;
          case EXCLUIDO -> antes;
          default -> null;
        };
    ContextoAuditoria contexto = capturarContexto(authentication, request);
    ItemAuditoria auditoria =
        new ItemAuditoria(
            resolverItemId(antes, depois),
            acao,
            new ItemAuditoria.Ator(
                contexto.ator().getId(), contexto.ator().getNome(), contexto.ator().getEmail()),
            new ItemAuditoria.ContextoRequisicao(
                Instant.now(clock), contexto.ipOrigem(), contexto.userAgent(), contexto.traceId()),
            new ItemAuditoria.ConteudoEvento(
                serializarJsonCanonico(alteracoes),
                snapshotEvento == null ? null : serializarJsonCanonico(snapshotEvento.campos())));
    return Optional.of(auditoriaRepository.save(auditoria));
  }

  private ContextoAuditoria capturarContexto(
      Authentication authentication, HttpServletRequest request) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao autenticado");
    }
    Usuario ator = ItemAccessUtils.buscarUsuarioAutenticado(authentication, usuarioRepository);
    String ipOrigem =
        valorLimitado(requestFingerprintService.resolveClientIp(request), MAX_IP_LENGTH, "unknown");
    String userAgent = valorLimitado(request.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH, null);
    return new ContextoAuditoria(ator, ipOrigem, userAgent, resolverTraceId());
  }

  private Map<String, ValorAlterado> calcularAlteracoes(
      ItemAuditSnapshot antes, ItemAuditSnapshot depois) {
    if (antes == null || depois == null) {
      return Map.of();
    }
    Map<String, ValorAlterado> alteracoes = new LinkedHashMap<>();
    for (Map.Entry<String, Object> campo : antes.campos().entrySet()) {
      Object valorDepois = depois.campos().get(campo.getKey());
      if (!Objects.equals(campo.getValue(), valorDepois)) {
        alteracoes.put(campo.getKey(), new ValorAlterado(campo.getValue(), valorDepois));
      }
    }
    for (Map.Entry<String, Object> campo : depois.campos().entrySet()) {
      if (!antes.campos().containsKey(campo.getKey())) {
        alteracoes.put(campo.getKey(), new ValorAlterado(null, campo.getValue()));
      }
    }
    return Collections.unmodifiableMap(alteracoes);
  }

  private void validarSnapshots(
      ItemAuditoriaAcao acao, ItemAuditSnapshot antes, ItemAuditSnapshot depois) {
    if (acao == ItemAuditoriaAcao.CRIADO && (antes != null || depois == null)) {
      throw new IllegalArgumentException("CRIADO exige somente snapshot posterior");
    }
    if (acao == ItemAuditoriaAcao.EXCLUIDO && (antes == null || depois != null)) {
      throw new IllegalArgumentException("EXCLUIDO exige somente snapshot anterior");
    }
    if (!acaoLifecycle(acao) && (antes == null || depois == null)) {
      throw new IllegalArgumentException("Alteracao exige snapshots anterior e posterior");
    }
    if (antes != null && depois != null && !antes.itemId().equals(depois.itemId())) {
      throw new IllegalArgumentException("Snapshots pertencem a itens diferentes");
    }
  }

  private UUID resolverItemId(ItemAuditSnapshot antes, ItemAuditSnapshot depois) {
    return antes == null ? depois.itemId() : antes.itemId();
  }

  private boolean acaoLifecycle(ItemAuditoriaAcao acao) {
    return acao == ItemAuditoriaAcao.CRIADO || acao == ItemAuditoriaAcao.EXCLUIDO;
  }

  private List<ArquivoSnapshot> snapshotArquivosItem(Item item) {
    Map<String, ArquivoSnapshot> arquivosPorChave = new LinkedHashMap<>();
    for (ItemArquivo arquivo : item.getArquivos()) {
      adicionarArquivo(arquivosPorChave, arquivo.getId(), arquivo.getCaminhoArquivoPdf(), "ITEM");
    }
    adicionarArquivo(arquivosPorChave, null, item.getCaminhoArquivoPdf(), "ITEM_LEGADO");
    return ordenarArquivos(arquivosPorChave.values());
  }

  private PagamentoSnapshot snapshotPagamento(Item item) {
    List<ParcelaSnapshot> parcelas = new ArrayList<>();
    for (ItemParcelaPagamento parcela : item.getParcelasPagamento()) {
      Map<String, ArquivoSnapshot> arquivosPorChave = new LinkedHashMap<>();
      for (ItemParcelaPagamentoArquivo arquivo : parcela.getArquivosComprovantes()) {
        adicionarArquivo(
            arquivosPorChave, arquivo.getId(), arquivo.getCaminhoArquivoPdf(), "PARCELA");
      }
      adicionarArquivo(arquivosPorChave, null, parcela.getCaminhoArquivoPdf(), "PARCELA_LEGADO");
      parcelas.add(
          new ParcelaSnapshot(
              parcela.getId(),
              parcela.getNumero(),
              normalizarValor(parcela.getValorParcela()),
              parcela.isPaga(),
              valorTexto(parcela.getContaOrigemPagamento()),
              ordenarArquivos(arquivosPorChave.values())));
    }
    parcelas.sort(
        Comparator.comparing(
                ParcelaSnapshot::numero, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(
                parcela -> valorTexto(parcela.id()),
                Comparator.nullsLast(Comparator.naturalOrder())));
    if (item.getFormaPagamento() == null && parcelas.isEmpty()) {
      return null;
    }
    return new PagamentoSnapshot(valorTexto(item.getFormaPagamento()), List.copyOf(parcelas));
  }

  private void adicionarArquivo(
      Map<String, ArquivoSnapshot> arquivosPorChave, UUID id, String chave, String origem) {
    if (chave == null || chave.isBlank()) {
      return;
    }
    String chaveNormalizada = chave.trim();
    ArquivoSnapshot novo =
        new ArquivoSnapshot(
            id,
            arquivoStorageService.resolverNomeArquivo(chaveNormalizada),
            chaveNormalizada,
            origem);
    arquivosPorChave.merge(
        chaveNormalizada,
        novo,
        (existente, substituto) -> existente.id() == null ? substituto : existente);
  }

  private List<ArquivoSnapshot> ordenarArquivos(java.util.Collection<ArquivoSnapshot> arquivos) {
    return arquivos.stream()
        .sorted(
            Comparator.comparing(ArquivoSnapshot::chaveArmazenamento)
                .thenComparing(
                    arquivo -> valorTexto(arquivo.id()),
                    Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
  }

  private BigDecimal normalizarValor(BigDecimal valor) {
    return valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP);
  }

  private String valorTexto(Object valor) {
    if (valor instanceof Enum<?> enumValue) {
      return enumValue.name();
    }
    return valor == null ? null : valor.toString();
  }

  private String valorLimitado(String valor, int limite, String fallback) {
    if (valor == null || valor.isBlank()) {
      return fallback;
    }
    String normalizado = valor.trim();
    int quantidadeCaracteres = normalizado.codePointCount(0, normalizado.length());
    if (quantidadeCaracteres <= limite) {
      return normalizado;
    }
    int fim = normalizado.offsetByCodePoints(0, limite);
    return normalizado.substring(0, fim);
  }

  private String resolverTraceId() {
    String traceId = MDC.get(TRACE_ID_MDC_KEY);
    if (traceId != null && !traceId.isBlank()) {
      String normalizado = traceId.trim().replace("-", "").toLowerCase(Locale.ROOT);
      if (normalizado.length() == TRACE_ID_LENGTH && normalizado.matches("^[0-9a-f]{32}$")) {
        return normalizado;
      }
    }
    return UUID.randomUUID().toString().replace("-", "");
  }

  private String serializarJsonCanonico(Object valor) {
    try {
      return objectMapper.writeValueAsString(valor);
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao serializar auditoria de item", exception);
    }
  }

  private record ContextoAuditoria(
      Usuario ator, String ipOrigem, String userAgent, String traceId) {}

  private record ValorAlterado(Object antes, Object depois) {}

  private record ArquivoSnapshot(UUID id, String nome, String chaveArmazenamento, String origem) {}

  private record ParcelaSnapshot(
      UUID id,
      Integer numero,
      BigDecimal valor,
      boolean paga,
      String contaOrigem,
      List<ArquivoSnapshot> arquivos) {}

  private record PagamentoSnapshot(String forma, List<ParcelaSnapshot> parcelas) {}
}
