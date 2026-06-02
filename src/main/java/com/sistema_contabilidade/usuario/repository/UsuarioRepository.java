package com.sistema_contabilidade.usuario.repository;

import com.sistema_contabilidade.usuario.model.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface UsuarioRepository
    extends JpaRepository<Usuario, UUID>, JpaSpecificationExecutor<Usuario> {

  @EntityGraph(attributePaths = {"roles", "roles.permissoes"})
  Optional<Usuario> findByEmail(String email);

  @EntityGraph(attributePaths = {"roles", "roles.permissoes"})
  Optional<Usuario> findWithRolesById(UUID id);

  @Transactional
  @Modifying
  @Query(
      value = "update usuarios set version = 0 where id = :id and version is null",
      nativeQuery = true)
  int initializeVersionIfNull(@Param("id") UUID id);

  @Query("select u.version from Usuario u where u.id = :id")
  Optional<Long> findVersionById(@Param("id") UUID id);

  boolean existsByEmail(String email);
}
