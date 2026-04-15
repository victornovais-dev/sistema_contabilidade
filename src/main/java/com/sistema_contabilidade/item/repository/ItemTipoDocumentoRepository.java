package com.sistema_contabilidade.item.repository;

import com.sistema_contabilidade.item.model.ItemTipoDocumento;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemTipoDocumentoRepository extends JpaRepository<ItemTipoDocumento, Long> {

  List<ItemTipoDocumento> findAllByOrderByOrdemAscNomeAsc();

  Optional<ItemTipoDocumento> findByNomeIgnoreCase(String nome);
}
