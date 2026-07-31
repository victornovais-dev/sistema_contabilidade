package com.sistema_contabilidade.database.crypto;

import com.sistema_contabilidade.database.crypto.service.BlindIndexService;

@FunctionalInterface
public interface BlindIndexAware {

  void synchronizeBlindIndexes(BlindIndexService blindIndexService);
}
