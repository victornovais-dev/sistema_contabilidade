package com.sistema_contabilidade.item.service;

import com.sistema_contabilidade.item.dto.ItemListPageResponse;
import com.sistema_contabilidade.rbac.service.CampaignScope;
import java.util.function.Supplier;

/** Cache boundary for item-list pages. */
public interface ItemListPageCache {

  ItemListPageResponse getOrCompute(
      CampaignScope scope, String normalizedFilters, Supplier<ItemListPageResponse> loader);

  void invalidateAfterItemWrite();

  static ItemListPageCache noOp() {
    return new ItemListPageCache() {
      @Override
      public ItemListPageResponse getOrCompute(
          CampaignScope scope, String normalizedFilters, Supplier<ItemListPageResponse> loader) {
        return loader.get();
      }

      @Override
      public void invalidateAfterItemWrite() {
        // No cache exists in focused unit tests.
      }
    };
  }
}
