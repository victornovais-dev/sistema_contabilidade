package com.sistema_contabilidade.item.repository;

import com.sistema_contabilidade.item.model.ItemArquivo;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemArquivoRepository extends JpaRepository<ItemArquivo, UUID> {
  List<ItemArquivo> findAllByItemId(UUID itemId);
}
