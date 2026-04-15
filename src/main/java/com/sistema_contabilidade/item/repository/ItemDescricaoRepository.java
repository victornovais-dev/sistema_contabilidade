package com.sistema_contabilidade.item.repository;

import com.sistema_contabilidade.item.model.ItemDescricao;
import com.sistema_contabilidade.item.model.TipoItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemDescricaoRepository extends JpaRepository<ItemDescricao, Long> {

  List<ItemDescricao> findByTipoOrderByOrdemAscNomeAsc(TipoItem tipo);

  Optional<ItemDescricao> findByTipoAndNomeIgnoreCase(TipoItem tipo, String nome);
}
