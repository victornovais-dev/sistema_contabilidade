package com.sistema_contabilidade.rbac.repository;

import com.sistema_contabilidade.rbac.model.Permissao;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissaoRepository extends JpaRepository<Permissao, UUID> {

  Optional<Permissao> findByNome(String nome);
}
