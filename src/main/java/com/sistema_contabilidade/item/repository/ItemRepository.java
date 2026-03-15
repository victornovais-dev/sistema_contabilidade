package com.sistema_contabilidade.item.repository;

import com.sistema_contabilidade.item.model.Item;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemRepository extends JpaRepository<Item, UUID> {}
