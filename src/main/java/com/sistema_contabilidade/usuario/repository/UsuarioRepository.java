package com.sistema_contabilidade.usuario.repository;

import com.sistema_contabilidade.database.crypto.BlindIndexes;
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

  String ROLE_ATTRIBUTE = "roles";
  String ROLE_PERMISSION_ATTRIBUTE = "roles.permissoes";

  @EntityGraph(attributePaths = {ROLE_ATTRIBUTE, ROLE_PERMISSION_ATTRIBUTE})
  Optional<Usuario> findByEmailBlindIndex(String emailBlindIndex);

  default Optional<Usuario> findByEmail(String email) {
    return findByEmailBlindIndex(BlindIndexes.email(email));
  }

  @EntityGraph(attributePaths = {ROLE_ATTRIBUTE, ROLE_PERMISSION_ATTRIBUTE})
  Optional<Usuario> findWithRolesById(UUID id);

  @EntityGraph(attributePaths = {ROLE_ATTRIBUTE, ROLE_PERMISSION_ATTRIBUTE})
  Optional<Usuario> findByCognitoSubBlindIndex(String cognitoSubBlindIndex);

  default Optional<Usuario> findByCognitoSub(String cognitoSub) {
    return findByCognitoSubBlindIndex(BlindIndexes.cognitoSub(cognitoSub));
  }

  @EntityGraph(attributePaths = {ROLE_ATTRIBUTE, ROLE_PERMISSION_ATTRIBUTE})
  Optional<Usuario> findByCognitoUsernameBlindIndex(String cognitoUsernameBlindIndex);

  default Optional<Usuario> findByCognitoUsername(String cognitoUsername) {
    return findByCognitoUsernameBlindIndex(BlindIndexes.cognitoUsername(cognitoUsername));
  }

  @Transactional
  @Modifying
  @Query(
      value = "update usuarios set version = 0 where id = :id and version is null",
      nativeQuery = true)
  int initializeVersionIfNull(@Param("id") UUID id);

  @Query("select u.version from Usuario u where u.id = :id")
  Optional<Long> findVersionById(@Param("id") UUID id);

  boolean existsByEmailBlindIndex(String emailBlindIndex);

  default boolean existsByEmail(String email) {
    return existsByEmailBlindIndex(BlindIndexes.email(email));
  }

  boolean existsByCognitoSubBlindIndex(String cognitoSubBlindIndex);

  default boolean existsByCognitoSub(String cognitoSub) {
    return existsByCognitoSubBlindIndex(BlindIndexes.cognitoSub(cognitoSub));
  }
}
