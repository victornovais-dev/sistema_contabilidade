package com.sistema_contabilidade.item.dto;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

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

  public static ItemListPageResponse empty(Pageable pageable) {
    return new ItemListPageResponse(
        List.of(),
        pageable.getPageNumber() + 1,
        pageable.getPageSize(),
        0,
        1,
        false,
        pageable.getPageNumber() > 0);
  }

  public static ItemListPageResponse fromSlice(Slice<ItemListResponse> slice) {
    long totalItems = estimateTotalItems(slice);
    int totalPages = estimateTotalPages(slice);
    return new ItemListPageResponse(
        slice.getContent(),
        slice.getNumber() + 1,
        slice.getSize(),
        totalItems,
        totalPages,
        slice.hasNext(),
        slice.hasPrevious());
  }

  private static long estimateTotalItems(Slice<ItemListResponse> slice) {
    long offset = (long) slice.getNumber() * slice.getSize();
    long visibleItems = slice.getNumberOfElements();
    return offset + visibleItems + (slice.hasNext() ? 1 : 0);
  }

  private static int estimateTotalPages(Slice<ItemListResponse> slice) {
    return Math.max(slice.getNumber() + 1 + (slice.hasNext() ? 1 : 0), 1);
  }
}
