package com.sistema_contabilidade.item.repository;

import com.sistema_contabilidade.item.model.TipoItem;
import java.time.LocalDate;
import java.util.Set;

public record ItemListPageQuery(
    Set<String> roleNomes,
    TipoItem tipo,
    LocalDate dataInicio,
    LocalDate dataFim,
    String descricao,
    String razao) {

  public ItemListPageQuery {
    roleNomes = roleNomes == null ? Set.of() : Set.copyOf(roleNomes);
  }

  @Override
  public Set<String> roleNomes() {
    return roleNomes;
  }
}
