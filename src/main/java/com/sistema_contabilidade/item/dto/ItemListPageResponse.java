package com.sistema_contabilidade.item.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record ItemListPageResponse(
    List<ItemListResponse> items,
    int page,
    int pageSize,
    long totalItems,
    int totalPages,
    boolean hasNext,
    boolean hasPrevious) {

  public ItemListPageResponse {
    items = items == null ? List.of() : List.copyOf(items);
  }

  public static ItemListPageResponse fromPage(Page<ItemListResponse> page) {
    return new ItemListPageResponse(
        page.getContent(),
        page.getNumber() + 1,
        page.getSize(),
        page.getTotalElements(),
        Math.max(page.getTotalPages(), 1),
        page.hasNext(),
        page.hasPrevious());
  }
}
