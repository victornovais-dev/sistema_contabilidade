package com.sistema_contabilidade.item.service;

import com.sistema_contabilidade.item.dto.ItemListPageRequest;
import com.sistema_contabilidade.item.dto.ItemListPageResponse;
import com.sistema_contabilidade.item.dto.ItemListResponse;
import com.sistema_contabilidade.item.repository.ItemListPageQuery;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.rbac.service.CandidateRoleCatalogService;
import com.sistema_contabilidade.security.validation.InputSanitizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ItemListService {

  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_PAGE_SIZE = 10;
  private static final Sort DEFAULT_SORT =
      Sort.by(Sort.Order.desc("horarioCriacao"), Sort.Order.desc("id"));

  private final ItemRepository itemRepository;
  private final CandidateRoleCatalogService candidateRoleCatalogService;
  private final InputSanitizer inputSanitizer;

  @Transactional(readOnly = true)
  public ItemListPageResponse listarItens(
      Authentication authentication, ItemListPageRequest request) {
    int page = request == null ? DEFAULT_PAGE : Math.max(request.getPage(), DEFAULT_PAGE);
    int pageSize =
        request == null ? DEFAULT_PAGE_SIZE : Math.max(request.getPageSize(), DEFAULT_PAGE_SIZE);
    String roleFiltroNormalizada = sanitizeRole(request == null ? null : request.getRole());
    String descricaoExata = sanitizeDescricao(request == null ? null : request.getDescricao());
    String razaoLike = sanitizeRazao(request == null ? null : request.getRazao());
    LocalDate dataInicio = request == null ? null : request.getDataInicio();
    LocalDate dataFim = request == null ? null : request.getDataFim();
    validarIntervaloDeDatas(dataInicio, dataFim);

    Pageable pageable = PageRequest.of(page - 1, pageSize, DEFAULT_SORT);
    Set<String> roleNomesVisiveis = resolveRoleScope(authentication, roleFiltroNormalizada);
    if (roleNomesVisiveis != null && roleNomesVisiveis.isEmpty()) {
      return ItemListPageResponse.empty(pageable);
    }

    ItemListPageQuery query =
        new ItemListPageQuery(
            roleNomesVisiveis,
            request == null ? null : request.getTipo(),
            dataInicio,
            dataFim,
            descricaoExata,
            razaoLike);

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
    if (ItemAccessUtils.isAdmin(authentication)) {
      return candidateRoleCatalogService.listAvailableRolesForAdmin();
    }

    return candidateRoleCatalogService.filterAvailableRoles(
        ItemAccessUtils.extrairRoleNomes(authentication));
  }

  private Set<String> resolveRoleScope(
      Authentication authentication, String roleFiltroNormalizada) {
    if (ItemAccessUtils.isAdmin(authentication)) {
      return roleFiltroNormalizada == null ? null : Set.of(roleFiltroNormalizada);
    }

    Set<String> roleNomesUsuario = ItemAccessUtils.extrairRoleNomes(authentication);
    if (roleNomesUsuario.isEmpty()) {
      return Set.of();
    }
    if (roleFiltroNormalizada != null) {
      ItemAccessUtils.validarRoleFiltro(roleFiltroNormalizada, roleNomesUsuario);
      return Set.of(roleFiltroNormalizada);
    }
    return roleNomesUsuario;
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
}
