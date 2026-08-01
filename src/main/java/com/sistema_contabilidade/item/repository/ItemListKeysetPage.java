package com.sistema_contabilidade.item.repository;

import com.sistema_contabilidade.item.dto.ItemListResponse;
import java.util.List;

public record ItemListKeysetPage(List<ItemListResponse> items, boolean hasMore) {

  public ItemListKeysetPage {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
