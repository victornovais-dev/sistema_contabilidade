package com.sistema_contabilidade.item.service;

import com.sistema_contabilidade.item.dto.ItemListCursorDirection;
import com.sistema_contabilidade.item.dto.ItemListPageRequest;
import com.sistema_contabilidade.item.dto.ItemListPageResponse;
import com.sistema_contabilidade.item.dto.ItemListResponse;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemListKeysetCursor;
import com.sistema_contabilidade.item.repository.ItemListKeysetPage;
import com.sistema_contabilidade.item.repository.ItemListPageQuery;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.rbac.service.CampaignScope;
import com.sistema_contabilidade.rbac.service.CampaignScopeResolver;
import com.sistema_contabilidade.security.validation.InputSanitizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ItemListService {

  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_PAGE_SIZE = 10;
  private static final int MAX_PAGE_SIZE = 100;
  private static final String CURSOR_REQUIRED_MESSAGE =
      "Use cursor para acessar paginas posteriores.";
  private static final Sort DEFAULT_SORT =
      Sort.by(Sort.Order.desc("horarioCriacao"), Sort.Order.desc("id"));

  private final ItemRepository itemRepository;
  private final CampaignScopeResolver campaignScopeResolver;
  private final InputSanitizer inputSanitizer;
  private final ItemListCursorService itemListCursorService;
  private final ItemListPageCache itemListPageCache;
  private final boolean legacyOffsetEnabled;

  @Autowired
  public ItemListService(
      ItemRepository itemRepository,
      CampaignScopeResolver campaignScopeResolver,
      InputSanitizer inputSanitizer,
      ItemListCursorService itemListCursorService,
      ItemListPageCache itemListPageCache,
      @Value("${app.item-list.legacy-offset-enabled:false}") boolean legacyOffsetEnabled) {
    this.itemRepository = itemRepository;
    this.campaignScopeResolver = campaignScopeResolver;
    this.inputSanitizer = inputSanitizer;
    this.itemListCursorService = itemListCursorService;
    this.itemListPageCache = itemListPageCache;
    this.legacyOffsetEnabled = legacyOffsetEnabled;
  }

  ItemListService(
      ItemRepository itemRepository,
      CampaignScopeResolver campaignScopeResolver,
      InputSanitizer inputSanitizer) {
    this(
        itemRepository,
        campaignScopeResolver,
        inputSanitizer,
        new ItemListCursorService(
            "0123456789ABCDEF0123456789ABCDEF", "", java.time.Clock.systemUTC()),
        ItemListPageCache.noOp(),
        false);
  }

  @Transactional(readOnly = true)
  public ItemListPageResponse listarItens(
      Authentication authentication, ItemListPageRequest request) {
    NormalizedListRequest normalizedRequest = normalizeRequest(request);

    CampaignScope scope = campaignScopeResolver.resolve(authentication, normalizedRequest.role());
    Set<String> roleNomesVisiveis = scope.queryCampaignNames();
    if (roleNomesVisiveis != null && roleNomesVisiveis.isEmpty()) {
      return ItemListPageResponse.empty(
          PageRequest.of(normalizedRequest.page() - 1, normalizedRequest.pageSize(), DEFAULT_SORT));
    }

    ItemListPageQuery query =
        new ItemListPageQuery(
            roleNomesVisiveis,
            normalizedRequest.tipo(),
            normalizedRequest.dataInicio(),
            normalizedRequest.dataFim(),
            normalizedRequest.descricao(),
            normalizedRequest.razao());
    return listarPagina(scope, query, normalizedRequest);
  }

  private ItemListPageResponse listarPagina(
      CampaignScope scope, ItemListPageQuery query, NormalizedListRequest normalizedRequest) {
    String cursor = normalizedRequest.cursor();
    if (cursor == null || cursor.isBlank()) {
      if (normalizedRequest.direction() == ItemListCursorDirection.PREVIOUS) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Cursor de paginacao obrigatorio.");
      }
      return itemListPageCache.getOrCompute(
          scope,
          normalizedFilters(normalizedRequest, null),
          () ->
              listarPrimeiraOuPaginaLegada(
                  query, normalizedRequest.page(), normalizedRequest.pageSize()));
    }
    ItemListKeysetCursor keysetCursor =
        itemListCursorService.parse(
            cursor, query, normalizedRequest.pageSize(), normalizedRequest.direction());
    return itemListPageCache.getOrCompute(
        scope,
        normalizedFilters(normalizedRequest, keysetCursor),
        () ->
            listarComCursor(
                query, normalizedRequest.page(), normalizedRequest.pageSize(), keysetCursor));
  }

  private String normalizedFilters(
      NormalizedListRequest request, ItemListKeysetCursor keysetCursor) {
    return String.join(
        "\u001f",
        "page=" + request.page(),
        "pageSize=" + request.pageSize(),
        "tipo=" + stringValue(request.tipo()),
        "dataInicio=" + stringValue(request.dataInicio()),
        "dataFim=" + stringValue(request.dataFim()),
        "descricao=" + stringValue(request.descricao()),
        "razao=" + stringValue(request.razao()),
        "direction=" + request.direction(),
        "cursorHorario=" + (keysetCursor == null ? "" : keysetCursor.horarioCriacao()),
        "cursorId=" + (keysetCursor == null ? "" : keysetCursor.id()));
  }

  private String stringValue(Object value) {
    return value == null ? "" : value.toString();
  }

  private ItemListPageResponse listarPrimeiraOuPaginaLegada(
      ItemListPageQuery query, int page, int pageSize) {
    if (page > DEFAULT_PAGE && !legacyOffsetEnabled) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, CURSOR_REQUIRED_MESSAGE);
    }
    if (page > DEFAULT_PAGE) {
      return listarComOffsetLegado(query, page, pageSize);
    }
    ItemListKeysetPage itemPage = itemRepository.findKeysetPageForList(query, null, pageSize);
    return toKeysetResponse(itemPage, query, page, pageSize, false, false);
  }

  private ItemListPageResponse listarComCursor(
      ItemListPageQuery query, int page, int pageSize, ItemListKeysetCursor cursor) {
    ItemListKeysetPage itemPage = itemRepository.findKeysetPageForList(query, cursor, pageSize);
    boolean hasPrevious = cursor.direction() == ItemListCursorDirection.NEXT || itemPage.hasMore();
    boolean hasNext = cursor.direction() == ItemListCursorDirection.PREVIOUS || itemPage.hasMore();
    return toKeysetResponse(itemPage, query, page, pageSize, hasNext, hasPrevious);
  }

  private ItemListPageResponse toKeysetResponse(
      ItemListKeysetPage itemPage,
      ItemListPageQuery query,
      int page,
      int pageSize,
      boolean hasNext,
      boolean hasPrevious) {
    List<ItemListResponse> items = itemPage.items();
    String nextCursor =
        hasNext && !items.isEmpty()
            ? itemListCursorService.create(query, pageSize, position(items.getLast()))
            : null;
    String previousCursor =
        hasPrevious && !items.isEmpty()
            ? itemListCursorService.create(query, pageSize, position(items.getFirst()))
            : null;
    return ItemListPageResponse.fromKeyset(
        items, page, pageSize, hasNext, hasPrevious, nextCursor, previousCursor);
  }

  private ItemListKeysetCursor position(ItemListResponse item) {
    return new ItemListKeysetCursor(item.horarioCriacao(), item.id(), ItemListCursorDirection.NEXT);
  }

  private ItemListPageResponse listarComOffsetLegado(
      ItemListPageQuery query, int page, int pageSize) {
    Pageable pageable = PageRequest.of(page - 1, pageSize, DEFAULT_SORT);
    Slice<ItemListResponse> itemPage = itemRepository.findPageForList(query, pageable);
    if (page > DEFAULT_PAGE && itemPage.isEmpty()) {
      itemPage =
          itemRepository.findPageForList(
              query,
              PageRequest.of(Math.max(pageable.getPageNumber() - 1, 0), pageSize, DEFAULT_SORT));
    }

    return ItemListPageResponse.fromSlice(itemPage);
  }

  @Transactional(readOnly = true)
  public List<String> listarRolesDisponiveis(Authentication authentication) {
    return campaignScopeResolver.listAvailableCampaigns(authentication);
  }

  private String sanitizeRole(String role) {
    return ItemAccessUtils.normalizarRole(inputSanitizer.sanitizeInlineText(role, "role", 100));
  }

  private String sanitizeDescricao(String descricao) {
    String sanitized = inputSanitizer.sanitizeInlineText(descricao, "descricao", 120);
    return sanitized == null || sanitized.isBlank() ? null : sanitized;
  }

  private String sanitizeRazao(String razao) {
    String sanitized = inputSanitizer.sanitizeInlineText(razao, "razao", 150);
    return sanitized == null || sanitized.isBlank() ? null : sanitized;
  }

  private void validarIntervaloDeDatas(LocalDate dataInicio, LocalDate dataFim) {
    if (dataInicio != null && dataFim != null && dataInicio.isAfter(dataFim)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "dataInicio nao pode ser maior que dataFim.");
    }
  }

  private int normalizePageSize(int pageSize) {
    return pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
  }

  private NormalizedListRequest normalizeRequest(ItemListPageRequest request) {
    if (request == null) {
      return new NormalizedListRequest(
          DEFAULT_PAGE,
          DEFAULT_PAGE_SIZE,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          ItemListCursorDirection.NEXT);
    }
    LocalDate dataInicio = request.getDataInicio();
    LocalDate dataFim = request.getDataFim();
    validarIntervaloDeDatas(dataInicio, dataFim);
    return new NormalizedListRequest(
        Math.max(request.getPage(), DEFAULT_PAGE),
        normalizePageSize(request.getPageSize()),
        request.getTipo(),
        sanitizeRole(request.getRole()),
        dataInicio,
        dataFim,
        sanitizeDescricao(request.getDescricao()),
        sanitizeRazao(request.getRazao()),
        request.getCursor(),
        request.getDirection() == null ? ItemListCursorDirection.NEXT : request.getDirection());
  }

  private record NormalizedListRequest(
      int page,
      int pageSize,
      TipoItem tipo,
      String role,
      LocalDate dataInicio,
      LocalDate dataFim,
      String descricao,
      String razao,
      String cursor,
      ItemListCursorDirection direction) {}
}
