package com.sistema_contabilidade.auth.repository;

import com.sistema_contabilidade.auth.model.SessaoUsuario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SessaoUsuarioRepository
    extends JpaRepository<SessaoUsuario, UUID>, JpaSpecificationExecutor<SessaoUsuario> {

  Optional<SessaoUsuario> findByIdAndRevogadaFalse(UUID id);

  List<SessaoUsuario> findAllByUsuarioIdAndRevogadaFalse(UUID usuarioId);
}
