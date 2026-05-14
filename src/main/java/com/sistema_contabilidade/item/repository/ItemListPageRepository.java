package com.sistema_contabilidade.item.repository;

import com.sistema_contabilidade.item.dto.ItemListResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@FunctionalInterface
public interface ItemListPageRepository {

  Page<ItemListResponse> findPageForList(ItemListPageQuery query, Pageable pageable);
}
