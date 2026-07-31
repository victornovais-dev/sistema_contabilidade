package com.sistema_contabilidade.database.crypto;

import com.sistema_contabilidade.database.crypto.service.BlindIndexService;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BlindIndexEntityListener {

  private BlindIndexService blindIndexService;

  public BlindIndexEntityListener() {}

  @Autowired
  public BlindIndexEntityListener(BlindIndexService blindIndexService) {
    this.blindIndexService = blindIndexService;
  }

  @PrePersist
  @PreUpdate
  public void synchronize(Object entity) {
    if (entity instanceof BlindIndexAware blindIndexAware) {
      BlindIndexService service =
          blindIndexService == null
              ? DatabaseCryptoRegistry.blindIndexService()
              : blindIndexService;
      blindIndexAware.synchronizeBlindIndexes(service);
    }
  }
}
