package com.sistema_contabilidade.item.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ItemAuditSnapshot(UUID itemId, Map<String, Object> campos) {

  public ItemAuditSnapshot {
    Objects.requireNonNull(itemId, "itemId");
    Objects.requireNonNull(campos, "campos");
    campos = Collections.unmodifiableMap(new LinkedHashMap<>(campos));
  }
}
