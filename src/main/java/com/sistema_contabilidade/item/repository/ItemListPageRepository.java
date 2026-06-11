package com.sistema_contabilidade.item.repository;

import com.sistema_contabilidade.item.dto.ItemListResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

@FunctionalInterface
public interface ItemListPageRepository {

  Slice<ItemListResponse> findPageForList(ItemListPageQuery query, Pageable pageable);
}
