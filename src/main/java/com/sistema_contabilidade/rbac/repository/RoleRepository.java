package com.sistema_contabilidade.rbac.repository;

import com.sistema_contabilidade.rbac.model.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

  @EntityGraph(attributePaths = {"permissoes"})
  Optional<Role> findByNome(String nome);
}
