package com.sistema_contabilidade.rbac.repository;

import com.sistema_contabilidade.rbac.model.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

  @EntityGraph(attributePaths = {"permissoes"})
  Optional<Role> findByNome(String nome);

  @EntityGraph(attributePaths = {"permissoes"})
  Optional<Role> findByNomeIgnoreCase(String nome);

  @Query(
      """
      select distinct upper(trim(r.nome))
      from Role r
      where r.nome is not null
        and trim(r.nome) <> ''
      order by upper(trim(r.nome))
      """)
  List<String> findAllRoleNamesOrdered();
}
