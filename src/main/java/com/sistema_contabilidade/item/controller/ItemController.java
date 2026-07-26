package com.sistema_contabilidade.item.controller;

import com.sistema_contabilidade.common.util.RevenueClassificationUtils;
import com.sistema_contabilidade.item.config.ItemTipoDocumentoCatalog;
import com.sistema_contabilidade.item.dto.ItemArquivoResponse;
import com.sistema_contabilidade.item.dto.ItemArquivosUploadRequest;
import com.sistema_contabilidade.item.dto.ItemListPageRequest;
import com.sistema_contabilidade.item.dto.ItemListPageResponse;
import com.sistema_contabilidade.item.dto.ItemObservacaoUpdateRequest;
import com.sistema_contabilidade.item.dto.ItemPagamentoParcelaUpdateRequest;
import com.sistema_contabilidade.item.dto.ItemPagamentoUpdateRequest;
import com.sistema_contabilidade.item.dto.ItemResponse;
import com.sistema_contabilidade.item.dto.ItemUpsertRequest;
import com.sistema_contabilidade.item.dto.ItemVerificacaoUpdateRequest;
import com.sistema_contabilidade.item.model.FormaPagamentoItem;
import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.ItemArquivo;
import com.sistema_contabilidade.item.model.ItemParcelaPagamento;
import com.sistema_contabilidade.item.model.ItemParcelaPagamentoArquivo;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemArquivoRepository;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.item.service.ArquivoStorageService;
import com.sistema_contabilidade.item.service.ItemAccessUtils;
import com.sistema_contabilidade.item.service.ItemDescricaoService;
import com.sistema_contabilidade.item.service.ItemListService;
import com.sistema_contabilidade.item.service.ItemTipoDocumentoService;
import com.sistema_contabilidade.notificacao.service.NotificacaoService;
import com.sistema_contabilidade.security.util.SecurityPaths;
import com.sistema_contabilidade.security.validation.InputSanitizer;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(SecurityPaths.ITEMS_API_BASE)
@Validated
@RequiredArgsConstructor
@Slf4j
public class ItemController {

  private static final int SINGLE_ROLE_COUNT = 1;
  private static final String ID_PATH = SecurityPaths.ID_PATH;
  private static final String ARQUIVO_PATH = ID_PATH + "/arquivo";
  private static final String ITEM_NAO_ENCONTRADO = "Item nao encontrado";
  private static final String ARQUIVO_ITEM_NAO_ENCONTRADO = "Arquivo do item nao encontrado";
  private static final String NOME_ARQUIVO_INVALIDO = "Nome do arquivo invalido";
  private static final String CACHE_CONTROL_PRIVATE_NO_STORE =
      "no-store, no-cache, must-revalidate";
  private static final String CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";
  private static final String NOSNIFF = "nosniff";
  private static final String CONTABIL_AUTHORITY = "ROLE_CONTABIL";
  private static final String SUPPORT_AUTHORITY = "ROLE_SUPPORT";
  private static final String CANDIDATO_AUTHORITY = "ROLE_CANDIDATO";
  private static final String CONTABIL_NAO_PODE_EXCLUIR_ITEM =
      "Usuario com role CONTABIL nao tem permissao para excluir comprovantes.";
  private static final String ITEM_VERIFICADO_NAO_PODE_SER_EXCLUIDO =
      "Comprovantes verificados nao podem ser excluidos.";
  private static final String ROLE_RESTRITA_NAO_PODE_DESMARCAR_ITEM =
      "Usuarios SUPPORT nao podem desmarcar comprovantes verificados.";
  private static final String CANDIDATO_NAO_PODE_ALTERAR_VERIFICACAO =
      "Usuarios CANDIDATO nao podem alterar verificacao de comprovantes.";
  private static final String ANEXO_OBRIGATORIO = "Anexe ao menos um comprovante em PDF.";
  private static final String CPF_DUPLICADO = "Ja existe um item cadastrado com este CPF.";
  private static final String VALOR_OBRIGATORIO = "Valor e obrigatorio";
  private static final String DATA_OBRIGATORIA = "Data e obrigatoria";
  private static final int MAXIMO_PARCELAS = 4;
  private static final BigDecimal MAXIMO_VALOR_PARCELA = new BigDecimal("5000000.00");
  private static final String PAGAMENTO_INVALIDO = "Dados de pagamento invalidos.";
  private static final String PAGAMENTO_APENAS_DESPESA =
      "Pagamento pode ser registrado somente para despesas.";
  private static final String FORMA_PAGAMENTO_NAO_PODE_SER_ALTERADA =
      "Desmarque, exclua os PDFs e salve antes de trocar entre a vista e parcelado.";

  private final ItemRepository itemRepository;
  private final ItemArquivoRepository itemArquivoRepository;
  private final ArquivoStorageService arquivoStorageService;
  private final ItemListService itemListService;
  private final ItemDescricaoService itemDescricaoService;
  private final ItemTipoDocumentoService itemTipoDocumentoService;
  private final NotificacaoService notificacaoService;
  private final UsuarioRepository usuarioRepository;
  private final InputSanitizer inputSanitizer;
  private final ObjectProvider<EntityManager> entityManagerProvider;

  @PostMapping
  @Transactional
  public ResponseEntity<ItemResponse> criar(
      Authentication authentication, @Valid @RequestBody ItemUpsertRequest request) {
    Usuario usuarioAutenticado =
        ItemAccessUtils.buscarUsuarioAutenticado(authentication, usuarioRepository);
    boolean contaFinanceira = isContaFinanceira(request);
    Item item = new Item();
    List<String> arquivosSalvos = List.of();
    validarCamposObrigatorios(request, contaFinanceira);
    String roleNomeItem =
        resolverRoleNomeItem(usuarioAutenticado, request.role(), null, contaFinanceira);
    validarDescricaoDisponivel();
    validarAnexoObrigatorioNaCriacao(request);
    validarCpfUnico(request.cnpjCpf(), null);
    aplicarCamposBase(item, request, contaFinanceira);
    item.setCriadoPor(usuarioAutenticado);
    item.setRoleNome(roleNomeItem);

    try {
      arquivosSalvos = atualizarArquivos(item, request.arquivosPdf(), request.nomesArquivos());
      Item salvo = itemRepository.save(item);
      if (salvo.getTipo() == TipoItem.RECEITA) {
        notificacaoService.registrarReceitaLancada(salvo);
      }
      URI location =
          ServletUriComponentsBuilder.fromCurrentRequest()
              .path(ID_PATH)
              .buildAndExpand(salvo.getId())
              .toUri();
      return ResponseEntity.created(location).body(ItemResponse.from(salvo));
    } catch (RuntimeException ex) {
      removerArquivosSemFalhar(arquivosSalvos);
      throw ex;
    }
  }

  @GetMapping
  public ResponseEntity<ItemListPageResponse> listarTodos(
      Authentication authentication, @Valid @ModelAttribute ItemListPageRequest request) {
    return ResponseEntity.ok(itemListService.listarItens(authentication, request));
  }

  @GetMapping("/roles")
  public ResponseEntity<List<String>> listarRolesDisponiveis(Authentication authentication) {
    return ResponseEntity.ok(itemListService.listarRolesDisponiveis(authentication));
  }

  @GetMapping("/descricoes")
  public ResponseEntity<List<String>> listarDescricoesPorTipo(@RequestParam("tipo") TipoItem tipo) {
    return ResponseEntity.ok(itemDescricaoService.listarDescricoesPorTipo(tipo));
  }

  @GetMapping("/tipos-documento")
  public ResponseEntity<List<String>> listarTiposDocumento(
      @RequestParam(name = "tipo", required = false) TipoItem tipo) {
    return ResponseEntity.ok(listarTiposDocumentoComFallback(tipo));
  }

  private List<String> listarTiposDocumentoComFallback(TipoItem tipo) {
    try {
      if (tipo != null) {
        return itemTipoDocumentoService.listarTiposDocumentoPorTipo(tipo);
      }
      return itemTipoDocumentoService.listarTiposDocumento();
    } catch (RuntimeException exception) {
      if (log.isWarnEnabled()) {
        log.warn(
            "Falha ao listar tipos de documento pelo service. Usando catalogo padrao.", exception);
      }
      List<ItemTipoDocumentoCatalog.ItemTipoDocumentoSeed> catalogo =
          tipo == null
              ? ItemTipoDocumentoCatalog.defaultDocumentTypes()
              : ItemTipoDocumentoCatalog.defaultDocumentTypesByTipo(tipo);
      return catalogo.stream().map(seed -> seed.nome()).toList();
    }
  }

  @GetMapping(ID_PATH)
  @Transactional(readOnly = true)
  public ResponseEntity<ItemResponse> buscarPorId(
      Authentication authentication, @PathVariable("id") UUID id) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    return ResponseEntity.ok(ItemResponse.from(item));
  }

  @GetMapping(ARQUIVO_PATH)
  @Transactional(readOnly = true)
  public ResponseEntity<InputStreamResource> baixarArquivo(
      Authentication authentication, @PathVariable("id") UUID id) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    String caminhoArquivoPdf = item.getCaminhoArquivoPdf();
    if (caminhoArquivoPdf == null || caminhoArquivoPdf.isBlank()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ARQUIVO_ITEM_NAO_ENCONTRADO);
    }

    byte[] arquivoPdf = arquivoStorageService.carregarPdf(caminhoArquivoPdf);
    String nomeArquivo = resolverNomeArquivo(caminhoArquivoPdf);

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
        .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_PRIVATE_NO_STORE)
        .header(CONTENT_TYPE_OPTIONS_HEADER, NOSNIFF)
        .body(new InputStreamResource(new ByteArrayInputStream(arquivoPdf)));
  }

  @GetMapping(ID_PATH + "/arquivos")
  @Transactional(readOnly = true)
  public ResponseEntity<List<ItemArquivoResponse>> listarArquivos(
      Authentication authentication, @PathVariable("id") UUID id) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    List<ItemArquivoResponse> response =
        item.getArquivos().stream().map(ItemArquivoResponse::from).toList();
    return ResponseEntity.ok(response);
  }

  @GetMapping(ID_PATH + "/arquivos/{arquivoId}")
  @Transactional(readOnly = true)
  public ResponseEntity<InputStreamResource> baixarArquivoPorId(
      Authentication authentication,
      @PathVariable("id") UUID id,
      @PathVariable("arquivoId") UUID arquivoId) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    ItemArquivo arquivo =
        itemArquivoRepository
            .findById(arquivoId)
            .filter(
                found -> found.getItem() != null && item.getId().equals(found.getItem().getId()))
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, ARQUIVO_ITEM_NAO_ENCONTRADO));
    byte[] arquivoPdf = arquivoStorageService.carregarPdf(arquivo.getCaminhoArquivoPdf());
    String nomeArquivo = resolverNomeArquivo(arquivo.getCaminhoArquivoPdf());

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
        .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_PRIVATE_NO_STORE)
        .header(CONTENT_TYPE_OPTIONS_HEADER, NOSNIFF)
        .body(new InputStreamResource(new ByteArrayInputStream(arquivoPdf)));
  }

  @GetMapping(ID_PATH + "/arquivos/download")
  @Transactional(readOnly = true)
  public ResponseEntity<InputStreamResource> baixarTodosArquivos(
      Authentication authentication, @PathVariable("id") UUID id) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    if (item.getArquivos().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ARQUIVO_ITEM_NAO_ENCONTRADO);
    }
    List<ItemArquivo> arquivos = new ArrayList<>(item.getArquivos());
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
      for (ItemArquivo arquivo : arquivos) {
        String caminho = arquivo.getCaminhoArquivoPdf();
        if (caminho == null || caminho.isBlank()) {
          continue;
        }
        byte[] conteudo = arquivoStorageService.carregarPdf(caminho);
        String nomeArquivo = arquivoStorageService.resolverNomeArquivo(caminho);
        zip.putNextEntry(new ZipEntry(nomeArquivo));
        zip.write(conteudo);
        zip.closeEntry();
      }
      String observacao = item.getObservacao();
      String observacaoTexto =
          observacao == null || observacao.isBlank() ? "Sem observacao registrada." : observacao;
      byte[] observacaoBytes = observacaoTexto.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      zip.putNextEntry(new ZipEntry("observacao.txt"));
      zip.write(observacaoBytes);
      zip.closeEntry();
    } catch (Exception ex) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao gerar arquivo ZIP", ex);
    }

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"comprovantes-" + id + ".zip\"")
        .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_PRIVATE_NO_STORE)
        .header(CONTENT_TYPE_OPTIONS_HEADER, NOSNIFF)
        .body(new InputStreamResource(new ByteArrayInputStream(outputStream.toByteArray())));
  }

  @PostMapping(ID_PATH + "/arquivos")
  @Transactional
  public ResponseEntity<List<ItemArquivoResponse>> adicionarArquivos(
      Authentication authentication,
      @PathVariable("id") UUID id,
      @Valid @RequestBody ItemArquivosUploadRequest request) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    List<byte[]> arquivosPdf = request.arquivosPdf();
    if (arquivosPdf == null || arquivosPdf.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie ao menos um PDF.");
    }
    List<String> arquivosSalvos = List.of();
    try {
      arquivosSalvos = arquivoStorageService.salvarPdfs(arquivosPdf, request.nomesArquivos());
      adicionarArquivosAoItem(item, arquivosSalvos);
      if (item.getCaminhoArquivoPdf() == null || item.getCaminhoArquivoPdf().isBlank()) {
        item.setCaminhoArquivoPdf(arquivosSalvos.getFirst());
      }
      Item salvo = itemRepository.save(item);
      List<ItemArquivoResponse> response =
          salvo.getArquivos().stream().map(ItemArquivoResponse::from).toList();
      return ResponseEntity.ok(response);
    } catch (RuntimeException ex) {
      removerArquivosSemFalhar(arquivosSalvos);
      throw ex;
    }
  }

  @DeleteMapping(ID_PATH + "/arquivos/{arquivoId}")
  @Transactional
  public ResponseEntity<Void> deletarArquivo(
      Authentication authentication,
      @PathVariable("id") UUID id,
      @PathVariable("arquivoId") UUID arquivoId) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    ItemArquivo arquivo =
        item.getArquivos().stream()
            .filter(entry -> arquivoId.equals(entry.getId()))
            .findFirst()
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, ARQUIVO_ITEM_NAO_ENCONTRADO));

    arquivoStorageService.deletarPdf(arquivo.getCaminhoArquivoPdf());
    item.getArquivos().removeIf(entry -> arquivoId.equals(entry.getId()));

    if (item.getCaminhoArquivoPdf() != null
        && item.getCaminhoArquivoPdf().equals(arquivo.getCaminhoArquivoPdf())) {
      String novoCaminho =
          item.getArquivos().isEmpty()
              ? null
              : item.getArquivos().getFirst().getCaminhoArquivoPdf();
      item.setCaminhoArquivoPdf(novoCaminho);
    }

    itemRepository.save(item);
    return ResponseEntity.noContent().build();
  }

  @PutMapping(ID_PATH)
  @Transactional
  public ResponseEntity<ItemResponse> atualizar(
      Authentication authentication,
      @PathVariable("id") UUID id,
      @Valid @RequestBody ItemUpsertRequest request) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    List<String> arquivosAntigos = listarArquivosPersistidos(item);
    List<String> arquivosNovos = List.of();
    Usuario usuarioAutenticado =
        ItemAccessUtils.buscarUsuarioAutenticado(authentication, usuarioRepository);
    boolean contaFinanceira = isContaFinanceira(request);
    validarCamposObrigatorios(request, contaFinanceira);
    String roleNomeItem =
        resolverRoleNomeItem(
            usuarioAutenticado, request.role(), item.getRoleNome(), contaFinanceira);
    validarDescricaoDisponivel();
    validarCpfUnico(request.cnpjCpf(), item.getId());
    aplicarCamposBase(item, request, contaFinanceira);
    item.setRoleNome(roleNomeItem);

    try {
      arquivosNovos = atualizarArquivos(item, request.arquivosPdf(), request.nomesArquivos());
      Item salvo = itemRepository.save(item);
      notificacaoService.sincronizarComItem(salvo);
      removerArquivosSubstituidos(arquivosAntigos, arquivosNovos);
      return ResponseEntity.ok(ItemResponse.from(salvo));
    } catch (RuntimeException ex) {
      removerArquivosSemFalhar(arquivosNovos);
      throw ex;
    }
  }

  @PatchMapping(ID_PATH + "/observacao")
  @Transactional
  public ResponseEntity<ItemResponse> atualizarObservacao(
      Authentication authentication,
      @PathVariable("id") UUID id,
      @Valid @RequestBody ItemObservacaoUpdateRequest request) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    item.setObservacao(
        inputSanitizer.sanitizeMultilineText(request.observacao(), "observacao", 500));
    Item salvo = itemRepository.save(item);
    return ResponseEntity.ok(ItemResponse.from(salvo));
  }

  @PatchMapping(ID_PATH + "/pagamento")
  @Transactional
  public ResponseEntity<ItemResponse> atualizarPagamento(
      Authentication authentication,
      @PathVariable("id") UUID id,
      @Valid @RequestBody ItemPagamentoUpdateRequest request) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    if (item.getTipo() != TipoItem.DESPESA) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PAGAMENTO_APENAS_DESPESA);
    }
    PagamentoApplyResult resultado = aplicarPagamento(item, request);
    try {
      Item salvo = itemRepository.save(item);
      removerArquivosSubstituidos(resultado.arquivosParaRemover(), resultado.arquivosNovos());
      return ResponseEntity.ok(ItemResponse.from(salvo));
    } catch (RuntimeException ex) {
      removerArquivosSemFalhar(resultado.arquivosNovos());
      throw ex;
    }
  }

  @PatchMapping(ID_PATH + "/verificacao")
  @Transactional
  public ResponseEntity<ItemResponse> atualizarVerificacao(
      Authentication authentication,
      @PathVariable("id") UUID id,
      @Valid @RequestBody ItemVerificacaoUpdateRequest request) {
    Item item = buscarItemAutorizadoPorId(id, authentication);
    validarPermissaoAtualizarVerificacao(authentication, item, request);
    item.setVerificado(Boolean.TRUE.equals(request.verificado()));
    Item salvo = itemRepository.save(item);
    notificacaoService.sincronizarComItem(salvo);
    return ResponseEntity.ok(ItemResponse.from(salvo));
  }

  @DeleteMapping(ID_PATH)
  @Transactional
  public ResponseEntity<Void> deletar(Authentication authentication, @PathVariable("id") UUID id) {
    validarPermissaoExcluirItem(authentication);
    Item item = buscarItemAutorizadoPorId(id, authentication);
    validarItemNaoVerificadoParaExclusao(item);
    List<String> arquivos = listarArquivosPersistidos(item);
    notificacaoService.removerPorItemId(item.getId());
    itemRepository.delete(item);
    removerArquivosSemFalhar(arquivos);
    return ResponseEntity.noContent().build();
  }

  private void validarPermissaoExcluirItem(Authentication authentication) {
    if (authentication == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao autenticado");
    }
    if (temAuthority(authentication, CONTABIL_AUTHORITY)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, CONTABIL_NAO_PODE_EXCLUIR_ITEM);
    }
  }

  private void validarPermissaoAtualizarVerificacao(
      Authentication authentication, Item item, ItemVerificacaoUpdateRequest request) {
    if (temAuthority(authentication, CANDIDATO_AUTHORITY)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, CANDIDATO_NAO_PODE_ALTERAR_VERIFICACAO);
    }
    boolean desmarcandoItemVerificado =
        item.isVerificado() && !Boolean.TRUE.equals(request.verificado());
    if (desmarcandoItemVerificado && temAuthority(authentication, SUPPORT_AUTHORITY)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, ROLE_RESTRITA_NAO_PODE_DESMARCAR_ITEM);
    }
  }

  private boolean temAuthority(Authentication authentication, String authorityName) {
    if (authentication == null) {
      return false;
    }
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> authorityName.equals(authority.getAuthority()));
  }

  private void validarItemNaoVerificadoParaExclusao(Item item) {
    if (item.isVerificado()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ITEM_VERIFICADO_NAO_PODE_SER_EXCLUIDO);
    }
  }

  private Item buscarItemAutorizadoPorId(UUID id, Authentication authentication) {
    Item item =
        itemRepository
            .findByIdComCriadorERoles(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, ITEM_NAO_ENCONTRADO));
    if (!podeAcessarItemPorEscopo(authentication, item)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ITEM_NAO_ENCONTRADO);
    }
    return recarregarItemComVersaoInicializadaSeNecessario(item);
  }

  private boolean podeAcessarItemPorEscopo(Authentication authentication, Item item) {
    return isAdmin(authentication)
        || temAuthority(authentication, CONTABIL_AUTHORITY)
        || temAcessoPorRole(authentication, item);
  }

  @SuppressWarnings("PMD.CloseResource")
  private Item recarregarItemComVersaoInicializadaSeNecessario(Item item) {
    if (item.getId() == null || item.getVersion() != null) {
      return item;
    }
    int linhasAtualizadas = itemRepository.initializeVersionIfNull(item.getId());
    EntityManager entityManager = entityManagerProvider.getIfAvailable();
    if (entityManager == null) {
      if (linhasAtualizadas > 0) {
        item.setVersion(0L);
        return item;
      }
      Long versionAtual = itemRepository.findVersionById(item.getId()).orElse(0L);
      item.setVersion(versionAtual);
      return item;
    }
    if (linhasAtualizadas > 0 || itemRepository.findVersionById(item.getId()).isPresent()) {
      entityManager.clear();
      return itemRepository
          .findByIdComCriadorERoles(item.getId())
          .orElseThrow(
              () -> new ResponseStatusException(HttpStatus.NOT_FOUND, ITEM_NAO_ENCONTRADO));
    }
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, ITEM_NAO_ENCONTRADO);
  }

  private boolean temAcessoPorRole(Authentication authentication, Item item) {
    Set<String> roleNomesUsuario =
        ItemAccessUtils.extrairRoleNomes(
            ItemAccessUtils.buscarUsuarioAutenticado(authentication, usuarioRepository));
    if (roleNomesUsuario.isEmpty()) {
      return false;
    }
    String roleNomeItem = ItemAccessUtils.normalizarRole(item.getRoleNome());
    return roleNomeItem != null && roleNomesUsuario.contains(roleNomeItem);
  }

  private boolean isAdmin(Authentication authentication) {
    return ItemAccessUtils.isAdmin(authentication);
  }

  private void validarRoleFiltro(String roleFiltro, Set<String> roleNomesUsuario) {
    ItemAccessUtils.validarRoleFiltro(roleFiltro, roleNomesUsuario);
  }

  private String resolverRoleNomeItem(
      Usuario usuarioAutenticado,
      String roleRequest,
      String roleAtualItem,
      boolean contaFinanceira) {
    Set<String> roleNomesUsuario = ItemAccessUtils.extrairRoleNomes(usuarioAutenticado);
    boolean usuarioAdmin =
        roleNomesUsuario.contains("ADMIN") || roleNomesUsuario.contains("ROLE_ADMIN");
    if (roleNomesUsuario.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Usuario autenticado nao possui role para vincular ao comprovante.");
    }

    String roleRequestNormalizada =
        ItemAccessUtils.normalizarRole(inputSanitizer.sanitizeInlineText(roleRequest, "role", 80));
    if (roleRequestNormalizada != null) {
      if (!usuarioAdmin) {
        validarRoleFiltro(roleRequestNormalizada, roleNomesUsuario);
      }
      return roleRequestNormalizada;
    }

    String roleAtualNormalizada = ItemAccessUtils.normalizarRole(roleAtualItem);
    if (roleAtualNormalizada != null) {
      return roleAtualNormalizada;
    }

    if (contaFinanceira) {
      if (usuarioAdmin) {
        return null;
      }
      return roleNomesUsuario.stream().sorted().findFirst().orElse(null);
    }

    if (roleNomesUsuario.size() == SINGLE_ROLE_COUNT) {
      return roleNomesUsuario.iterator().next();
    }

    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "Selecione a role responsavel por este comprovante.");
  }

  private void aplicarCamposBase(Item item, ItemUpsertRequest request, boolean contaFinanceira) {
    item.setValor(contaFinanceira ? BigDecimal.ZERO : request.valor());
    item.setData(contaFinanceira ? request.horarioCriacao().toLocalDate() : request.data());
    item.setHorarioCriacao(request.horarioCriacao());
    item.setTipo(request.tipo());
    item.setDescricao(inputSanitizer.sanitizeInlineText(request.descricao(), "descricao", 120));
    item.setTipoDocumento(
        contaFinanceira
            ? null
            : inputSanitizer.sanitizeInlineText(request.tipoDocumento(), "tipoDocumento", 120));
    item.setNumeroDocumento(
        contaFinanceira
            ? null
            : inputSanitizer.sanitizeInlineText(request.numeroDocumento(), "numeroDocumento", 50));
    item.setRazaoSocialNome(
        contaFinanceira
            ? null
            : inputSanitizer.sanitizeInlineText(request.razaoSocialNome(), "razaoSocialNome", 150));
    item.setCnpjCpf(
        contaFinanceira
            ? null
            : inputSanitizer.sanitizeInlineText(request.cnpjCpf(), "cnpjCpf", 32));
    item.setObservacao(
        inputSanitizer.sanitizeMultilineText(request.observacao(), "observacao", 500));
  }

  private void validarCamposObrigatorios(ItemUpsertRequest request, boolean contaFinanceira) {
    if (!contaFinanceira && request.valor() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, VALOR_OBRIGATORIO);
    }
    if (!contaFinanceira && request.data() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, DATA_OBRIGATORIA);
    }
  }

  private boolean isContaFinanceira(ItemUpsertRequest request) {
    return request != null
        && request.tipo() == TipoItem.RECEITA
        && RevenueClassificationUtils.isFinancialRevenue(request.descricao())
        && request.valor() == null
        && request.data() == null;
  }

  private void validarAnexoObrigatorioNaCriacao(ItemUpsertRequest request) {
    List<byte[]> arquivosPdf = request.arquivosPdf();
    if (arquivosPdf == null || arquivosPdf.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ANEXO_OBRIGATORIO);
    }
  }

  private void validarDescricaoDisponivel() {
    // Descricoes de receita sao validadas pela UI e pelo catalogo/fluxo de negocio.
  }

  private void validarCpfUnico(String cnpjCpf, UUID itemIdIgnorado) {
    String documentoNormalizado = normalizarDocumento(cnpjCpf);
    if (!isCpf(documentoNormalizado)) {
      return;
    }

    long quantidade =
        itemIdIgnorado == null
            ? itemRepository.countByDocumentoNormalizado(documentoNormalizado)
            : itemRepository.countByDocumentoNormalizadoAndIdNot(
                documentoNormalizado, itemIdIgnorado);
    if (quantidade > 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, CPF_DUPLICADO);
    }
  }

  private String normalizarDocumento(String documento) {
    String documentoSanitizado = inputSanitizer.sanitizeInlineText(documento, "cnpjCpf", 32);
    if (documentoSanitizado == null) {
      return "";
    }
    return documentoSanitizado.replaceAll("\\D", "");
  }

  private boolean isCpf(String documentoNormalizado) {
    return documentoNormalizado.length() == 11;
  }

  private PagamentoApplyResult aplicarPagamento(Item item, ItemPagamentoUpdateRequest request) {
    PagamentoContext context = prepararPagamento(item, request);
    List<String> arquivosNovos = new ArrayList<>();
    List<String> arquivosParaRemover = new ArrayList<>();

    try {
      List<ItemParcelaPagamento> parcelasAtualizadas =
          atualizarParcelas(item, context, arquivosNovos, arquivosParaRemover);
      removerArquivosDasParcelasExcluidas(item, context.quantidadeParcelas(), arquivosParaRemover);
      substituirParcelas(item, context.formaPagamento(), parcelasAtualizadas);
    } catch (RuntimeException ex) {
      removerArquivosSemFalhar(arquivosNovos);
      throw ex;
    }
    return new PagamentoApplyResult(List.copyOf(arquivosNovos), List.copyOf(arquivosParaRemover));
  }

  private PagamentoContext prepararPagamento(Item item, ItemPagamentoUpdateRequest request) {
    validarTrocaFormaPagamento(item, request.formaPagamento());
    int quantidadeParcelas = resolveQuantidadeParcelas(request);
    Map<Integer, ItemPagamentoParcelaUpdateRequest> requestPorNumero =
        mapearParcelasSolicitadas(request.parcelas(), quantidadeParcelas);
    List<BigDecimal> valoresParcelas =
        resolverValoresParcelas(item, request, requestPorNumero, quantidadeParcelas);
    return new PagamentoContext(
        request.formaPagamento(),
        quantidadeParcelas,
        requestPorNumero,
        mapearParcelasExistentes(item),
        valoresParcelas);
  }

  private void validarTrocaFormaPagamento(Item item, FormaPagamentoItem formaPagamentoSolicitada) {
    if (item.getFormaPagamento() == null
        || item.getFormaPagamento() == formaPagamentoSolicitada
        || modalidadePagamentoEstaLimpa(item)) {
      return;
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST, FORMA_PAGAMENTO_NAO_PODE_SER_ALTERADA);
  }

  private boolean modalidadePagamentoEstaLimpa(Item item) {
    return item.getParcelasPagamento().stream()
        .noneMatch(parcela -> parcela.isPaga() || possuiAnexoPagamento(parcela));
  }

  private Map<Integer, ItemPagamentoParcelaUpdateRequest> mapearParcelasSolicitadas(
      List<ItemPagamentoParcelaUpdateRequest> parcelasRequest, int quantidadeParcelas) {
    if (parcelasRequest.size() != quantidadeParcelas) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PAGAMENTO_INVALIDO);
    }
    Map<Integer, ItemPagamentoParcelaUpdateRequest> parcelasPorNumero = new HashMap<>();
    for (ItemPagamentoParcelaUpdateRequest parcelaRequest : parcelasRequest) {
      adicionarParcelaSolicitada(parcelasPorNumero, parcelaRequest, quantidadeParcelas);
    }
    return parcelasPorNumero;
  }

  private void adicionarParcelaSolicitada(
      Map<Integer, ItemPagamentoParcelaUpdateRequest> parcelasPorNumero,
      ItemPagamentoParcelaUpdateRequest parcelaRequest,
      int quantidadeParcelas) {
    Integer numero = parcelaRequest == null ? null : parcelaRequest.numero();
    if (numero == null || numero < 1 || numero > quantidadeParcelas) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PAGAMENTO_INVALIDO);
    }
    if (parcelasPorNumero.put(numero, parcelaRequest) != null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PAGAMENTO_INVALIDO);
    }
  }

  private Map<Integer, ItemParcelaPagamento> mapearParcelasExistentes(Item item) {
    Map<Integer, ItemParcelaPagamento> parcelasPorNumero = new HashMap<>();
    for (ItemParcelaPagamento parcela : item.getParcelasPagamento()) {
      if (parcela.getNumero() != null) {
        parcelasPorNumero.put(parcela.getNumero(), parcela);
      }
    }
    return parcelasPorNumero;
  }

  private List<ItemParcelaPagamento> atualizarParcelas(
      Item item,
      PagamentoContext context,
      List<String> arquivosNovos,
      List<String> arquivosParaRemover) {
    List<ItemParcelaPagamento> parcelasAtualizadas = new ArrayList<>();
    for (int indice = 1; indice <= context.quantidadeParcelas(); indice++) {
      parcelasAtualizadas.add(
          atualizarParcela(item, context, indice, arquivosNovos, arquivosParaRemover));
    }
    return parcelasAtualizadas;
  }

  private ItemParcelaPagamento atualizarParcela(
      Item item,
      PagamentoContext context,
      int indice,
      List<String> arquivosNovos,
      List<String> arquivosParaRemover) {
    ItemPagamentoParcelaUpdateRequest parcelaRequest = context.requestPorNumero().get(indice);
    ItemParcelaPagamento parcela =
        context.parcelaExistentePorNumero().getOrDefault(indice, new ItemParcelaPagamento());
    boolean paga = Boolean.TRUE.equals(parcelaRequest.paga());
    BigDecimal valorParcela = context.valoresParcelas().get(indice - 1);
    validarParcelaPaga(parcelaRequest, paga, valorParcela);
    parcela.setItem(item);
    parcela.setNumero(indice);
    parcela.setValorParcela(valorParcela);
    parcela.setPaga(paga);
    parcela.setContaOrigemPagamento(paga ? parcelaRequest.contaOrigemPagamento() : null);
    atualizarArquivosPagamento(parcela, parcelaRequest, paga, arquivosNovos, arquivosParaRemover);
    validarAnexoParcelaPaga(parcela, paga);
    return parcela;
  }

  private void validarParcelaPaga(
      ItemPagamentoParcelaUpdateRequest parcelaRequest, boolean paga, BigDecimal valorParcela) {
    if (!paga) {
      return;
    }
    if (parcelaRequest.contaOrigemPagamento() == null
        || valorParcela == null
        || valorParcela.signum() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PAGAMENTO_INVALIDO);
    }
  }

  private void validarAnexoParcelaPaga(ItemParcelaPagamento parcela, boolean paga) {
    if (paga && !possuiAnexoPagamento(parcela)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PAGAMENTO_INVALIDO);
    }
  }

  private boolean possuiAnexoPagamento(ItemParcelaPagamento parcela) {
    return (parcela.getCaminhoArquivoPdf() != null && !parcela.getCaminhoArquivoPdf().isBlank())
        || !parcela.getArquivosComprovantes().isEmpty();
  }

  private void removerArquivosDasParcelasExcluidas(
      Item item, int quantidadeParcelas, List<String> arquivosParaRemover) {
    for (ItemParcelaPagamento parcelaExistente : new ArrayList<>(item.getParcelasPagamento())) {
      Integer numero = parcelaExistente.getNumero();
      if (numero == null || numero > quantidadeParcelas) {
        adicionarArquivosPagamentoParaRemover(parcelaExistente, arquivosParaRemover);
      }
    }
  }

  private void substituirParcelas(
      Item item,
      FormaPagamentoItem formaPagamento,
      List<ItemParcelaPagamento> parcelasAtualizadas) {
    item.setFormaPagamento(formaPagamento);
    item.getParcelasPagamento().clear();
    item.getParcelasPagamento().addAll(parcelasAtualizadas);
    item.getParcelasPagamento().sort(Comparator.comparing(ItemParcelaPagamento::getNumero));
  }

  private int resolveQuantidadeParcelas(ItemPagamentoUpdateRequest request) {
    if (request.formaPagamento() == FormaPagamentoItem.AVISTA) {
      return 1;
    }
    Integer quantidadeParcelas = request.quantidadeParcelas();
    if (quantidadeParcelas == null
        || quantidadeParcelas < 2
        || quantidadeParcelas > MAXIMO_PARCELAS) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PAGAMENTO_INVALIDO);
    }
    return quantidadeParcelas;
  }

  private List<BigDecimal> resolverValoresParcelas(
      Item item,
      ItemPagamentoUpdateRequest request,
      Map<Integer, ItemPagamentoParcelaUpdateRequest> requestPorNumero,
      int quantidadeParcelas) {
    if (request.formaPagamento() == FormaPagamentoItem.AVISTA) {
      return validarValoresParcelas(calcularValoresParcelas(item.getValor(), quantidadeParcelas));
    }
    List<BigDecimal> valoresInformados = new ArrayList<>(quantidadeParcelas);
    boolean possuiValorInformado = false;
    for (int indice = 1; indice <= quantidadeParcelas; indice++) {
      BigDecimal valor = requestPorNumero.get(indice).valorParcela();
      possuiValorInformado |= valor != null;
      valoresInformados.add(valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP));
    }
    if (!possuiValorInformado) {
      return validarValoresParcelas(calcularValoresParcelas(item.getValor(), quantidadeParcelas));
    }
    if (valoresInformados.stream().anyMatch(valor -> valor == null || valor.signum() <= 0)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PAGAMENTO_INVALIDO);
    }
    BigDecimal totalInformado = valoresInformados.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal valorItem =
        item.getValor() == null
            ? BigDecimal.ZERO
            : item.getValor().setScale(2, RoundingMode.HALF_UP);
    if (totalInformado.compareTo(valorItem) != 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PAGAMENTO_INVALIDO);
    }
    return validarValoresParcelas(valoresInformados);
  }

  private List<BigDecimal> validarValoresParcelas(List<BigDecimal> valoresParcelas) {
    if (valoresParcelas.stream().anyMatch(valor -> valor.compareTo(MAXIMO_VALOR_PARCELA) > 0)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PAGAMENTO_INVALIDO);
    }
    return valoresParcelas;
  }

  private List<BigDecimal> calcularValoresParcelas(BigDecimal valorTotal, int quantidadeParcelas) {
    BigDecimal total =
        valorTotal == null ? BigDecimal.ZERO : valorTotal.setScale(2, RoundingMode.HALF_UP);
    long totalCentavos = total.movePointRight(2).longValueExact();
    long valorBase = totalCentavos / quantidadeParcelas;
    long resto = totalCentavos % quantidadeParcelas;
    List<BigDecimal> parcelas = new ArrayList<>(quantidadeParcelas);
    for (int indice = 1; indice <= quantidadeParcelas; indice++) {
      long centavos = valorBase + (indice <= resto ? 1 : 0);
      parcelas.add(BigDecimal.valueOf(centavos, 2));
    }
    return parcelas;
  }

  private void atualizarArquivosPagamento(
      ItemParcelaPagamento parcela,
      ItemPagamentoParcelaUpdateRequest request,
      boolean paga,
      List<String> arquivosNovos,
      List<String> arquivosParaRemover) {
    if (!paga) {
      adicionarArquivosPagamentoParaRemover(parcela, arquivosParaRemover);
      parcela.setCaminhoArquivoPdf(null);
      parcela.getArquivosComprovantes().clear();
      return;
    }
    if (Boolean.TRUE.equals(request.removerArquivoLegado())) {
      adicionarCaminhoSeValido(parcela.getCaminhoArquivoPdf(), arquivosParaRemover);
      parcela.setCaminhoArquivoPdf(null);
    }
    removerArquivosPagamentoSelecionados(parcela, request.arquivosRemovidos(), arquivosParaRemover);

    List<byte[]> arquivosPdf = request.arquivosPdf();
    List<String> nomesArquivos = request.nomesArquivos();
    boolean uploadLegado = arquivosPdf == null || arquivosPdf.isEmpty();
    if (uploadLegado && request.arquivoPdf() != null && request.arquivoPdf().length > 0) {
      arquivosPdf = List.of(request.arquivoPdf());
      nomesArquivos = List.of(request.nomeArquivo());
    }
    if (arquivosPdf == null || arquivosPdf.isEmpty()) {
      return;
    }
    if (nomesArquivos == null || arquivosPdf.size() != nomesArquivos.size()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PAGAMENTO_INVALIDO);
    }
    List<String> nomesSanitizados =
        nomesArquivos.stream()
            .map(nome -> inputSanitizer.sanitizeInlineText(nome, "nomeArquivo", 255))
            .map(nome -> nome == null || nome.isBlank() ? "parcela.pdf" : nome)
            .toList();
    List<String> caminhosNovos = arquivoStorageService.salvarPdfs(arquivosPdf, nomesSanitizados);
    if (caminhosNovos.size() != arquivosPdf.size()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie ao menos um PDF.");
    }
    arquivosNovos.addAll(caminhosNovos);
    if (uploadLegado) {
      adicionarCaminhoSeValido(parcela.getCaminhoArquivoPdf(), arquivosParaRemover);
      parcela.setCaminhoArquivoPdf(caminhosNovos.getFirst());
      return;
    }
    for (String caminho : caminhosNovos) {
      ItemParcelaPagamentoArquivo arquivo = new ItemParcelaPagamentoArquivo();
      arquivo.setParcelaPagamento(parcela);
      arquivo.setCaminhoArquivoPdf(caminho);
      parcela.getArquivosComprovantes().add(arquivo);
    }
  }

  private void removerArquivosPagamentoSelecionados(
      ItemParcelaPagamento parcela,
      List<UUID> arquivosRemovidos,
      List<String> arquivosParaRemover) {
    if (arquivosRemovidos == null || arquivosRemovidos.isEmpty()) {
      return;
    }
    Set<UUID> idsParaRemover = Set.copyOf(arquivosRemovidos);
    parcela
        .getArquivosComprovantes()
        .removeIf(
            arquivo -> {
              if (!idsParaRemover.contains(arquivo.getId())) {
                return false;
              }
              adicionarCaminhoSeValido(arquivo.getCaminhoArquivoPdf(), arquivosParaRemover);
              return true;
            });
  }

  private void adicionarArquivosPagamentoParaRemover(
      ItemParcelaPagamento parcela, List<String> arquivosParaRemover) {
    adicionarCaminhoSeValido(parcela.getCaminhoArquivoPdf(), arquivosParaRemover);
    parcela
        .getArquivosComprovantes()
        .forEach(
            arquivo ->
                adicionarCaminhoSeValido(arquivo.getCaminhoArquivoPdf(), arquivosParaRemover));
  }

  private void adicionarCaminhoSeValido(String caminho, List<String> caminhos) {
    if (caminho != null && !caminho.isBlank() && !caminhos.contains(caminho)) {
      caminhos.add(caminho);
    }
  }

  private record PagamentoApplyResult(
      List<String> arquivosNovos, List<String> arquivosParaRemover) {}

  private record PagamentoContext(
      FormaPagamentoItem formaPagamento,
      int quantidadeParcelas,
      Map<Integer, ItemPagamentoParcelaUpdateRequest> requestPorNumero,
      Map<Integer, ItemParcelaPagamento> parcelaExistentePorNumero,
      List<BigDecimal> valoresParcelas) {}

  private List<String> atualizarArquivos(
      Item item, List<byte[]> arquivosPdf, List<String> nomesArquivos) {
    item.getArquivos().clear();
    List<String> caminhos = arquivoStorageService.salvarPdfs(arquivosPdf, nomesArquivos);
    adicionarArquivosAoItem(item, caminhos);
    item.setCaminhoArquivoPdf(caminhos.isEmpty() ? null : caminhos.getFirst());
    return caminhos;
  }

  private void adicionarArquivosAoItem(Item item, List<String> caminhos) {
    for (String caminho : caminhos) {
      ItemArquivo arquivo = new ItemArquivo();
      arquivo.setCaminhoArquivoPdf(caminho);
      arquivo.setItem(item);
      item.getArquivos().add(arquivo);
    }
  }

  private String resolverNomeArquivo(String chaveArquivo) {
    String nomeArquivo = arquivoStorageService.resolverNomeArquivo(chaveArquivo);
    if (nomeArquivo == null || nomeArquivo.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NOME_ARQUIVO_INVALIDO);
    }
    return nomeArquivo;
  }

  private List<String> listarArquivosPersistidos(Item item) {
    List<String> caminhos = new ArrayList<>();
    if (item.getCaminhoArquivoPdf() != null && !item.getCaminhoArquivoPdf().isBlank()) {
      caminhos.add(item.getCaminhoArquivoPdf());
    }
    for (ItemArquivo arquivo : item.getArquivos()) {
      String caminho = arquivo.getCaminhoArquivoPdf();
      if (caminho != null && !caminho.isBlank() && !caminhos.contains(caminho)) {
        caminhos.add(caminho);
      }
    }
    for (ItemParcelaPagamento parcela : item.getParcelasPagamento()) {
      adicionarCaminhoSeValido(parcela.getCaminhoArquivoPdf(), caminhos);
      parcela
          .getArquivosComprovantes()
          .forEach(arquivo -> adicionarCaminhoSeValido(arquivo.getCaminhoArquivoPdf(), caminhos));
    }
    return caminhos;
  }

  private void removerArquivosSubstituidos(
      List<String> arquivosAntigos, List<String> arquivosNovos) {
    for (String caminhoAntigo : arquivosAntigos) {
      if (!arquivosNovos.contains(caminhoAntigo)) {
        arquivoStorageService.deletarPdf(caminhoAntigo);
      }
    }
  }

  private void removerArquivosSemFalhar(List<String> arquivos) {
    for (String caminho : arquivos) {
      try {
        arquivoStorageService.deletarPdf(caminho);
      } catch (RuntimeException ex) {
        if (log.isWarnEnabled()) {
          log.warn("Falha ao limpar arquivo apos erro de persistencia: {}", caminho, ex);
        }
      }
    }
  }
}
