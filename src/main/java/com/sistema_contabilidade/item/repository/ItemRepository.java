package com.sistema_contabilidade.item.repository;

import com.sistema_contabilidade.item.model.Item;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemRepository extends JpaRepository<Item, UUID> {

  @Query(
      """
      select distinct i
      from Item i
      join i.criadoPor u
      join u.roles r
      where r.nome in :roleNomes
      """)
  List<Item> findAllVisiveisPorRoleNomes(@Param("roleNomes") Set<String> roleNomes);

  @Query(
      """
      select i
      from Item i
      left join fetch i.criadoPor u
      left join fetch u.roles
      where i.id = :id
      """)
  Optional<Item> findByIdComCriadorERoles(@Param("id") UUID id);
}
