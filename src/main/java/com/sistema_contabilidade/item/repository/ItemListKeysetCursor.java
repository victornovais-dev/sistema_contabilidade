package com.sistema_contabilidade.item.repository;

import com.sistema_contabilidade.item.dto.ItemListCursorDirection;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record ItemListKeysetCursor(
    LocalDateTime horarioCriacao, UUID id, ItemListCursorDirection direction) {

  public ItemListKeysetCursor {
    Objects.requireNonNull(horarioCriacao, "horarioCriacao");
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(direction, "direction");
  }
}
