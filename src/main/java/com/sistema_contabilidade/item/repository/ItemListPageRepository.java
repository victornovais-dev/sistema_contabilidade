package com.sistema_contabilidade.item.repository;

import com.sistema_contabilidade.item.dto.ItemListResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface ItemListPageRepository {

  long countForList(ItemListPageQuery query);

  Slice<ItemListResponse> findPageForList(ItemListPageQuery query, Pageable pageable);

  ItemListKeysetPage findKeysetPageForList(
      ItemListPageQuery query, ItemListKeysetCursor cursor, int pageSize);
}
