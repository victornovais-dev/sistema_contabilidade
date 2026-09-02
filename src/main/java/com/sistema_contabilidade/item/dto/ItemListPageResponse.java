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
    boolean hasPrevious,
    String nextCursor,
    String previousCursor) {

  public ItemListPageResponse {
    items = items == null ? List.of() : List.copyOf(items);
  }

  public ItemListPageResponse(
      List<ItemListResponse> items,
      int page,
      int pageSize,
      long totalItems,
      int totalPages,
      boolean hasNext,
      boolean hasPrevious) {
    this(items, page, pageSize, totalItems, totalPages, hasNext, hasPrevious, null, null);
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

  public static ItemListPageResponse fromKeyset(
      List<ItemListResponse> items,
      int page,
      int pageSize,
      long totalItems,
      boolean hasNext,
      boolean hasPrevious,
      String nextCursor,
      String previousCursor) {
    int totalPages = totalPages(totalItems, pageSize);
    return new ItemListPageResponse(
        items,
        page,
        pageSize,
        totalItems,
        totalPages,
        hasNext,
        hasPrevious,
        nextCursor,
        previousCursor);
  }

  public static ItemListPageResponse fromSlice(Slice<ItemListResponse> slice, long totalItems) {
    int totalPages = totalPages(totalItems, slice.getSize());
    return new ItemListPageResponse(
        slice.getContent(),
        slice.getNumber() + 1,
        slice.getSize(),
        totalItems,
        totalPages,
        slice.hasNext(),
        slice.hasPrevious());
  }

  private static int totalPages(long totalItems, int pageSize) {
    if (totalItems <= 0 || pageSize <= 0) {
      return 1;
    }
    long totalPages = 1 + (totalItems - 1) / pageSize;
    return totalPages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalPages;
  }
}
