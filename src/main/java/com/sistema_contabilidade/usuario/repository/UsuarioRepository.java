package com.sistema_contabilidade.usuario.repository;

import com.sistema_contabilidade.usuario.model.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface UsuarioRepository
    extends JpaRepository<Usuario, UUID>, JpaSpecificationExecutor<Usuario> {

  @EntityGraph(attributePaths = {"roles", "roles.permissoes"})
  Optional<Usuario> findByEmail(String email);

  @EntityGraph(attributePaths = {"roles", "roles.permissoes"})
  Optional<Usuario> findWithRolesById(UUID id);

  boolean existsByEmail(String email);
}
