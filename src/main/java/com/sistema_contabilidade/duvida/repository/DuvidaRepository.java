package com.sistema_contabilidade.duvida.repository;

import com.sistema_contabilidade.duvida.model.Duvida;
import com.sistema_contabilidade.duvida.model.DuvidaStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DuvidaRepository extends JpaRepository<Duvida, UUID> {

  @Query(
      """
      select d from Duvida d
      where (:status is null or d.status = :status)
        and (:termo = ''
          or lower(d.nome) like lower(concat('%', :termo, '%'))
          or lower(d.email) like lower(concat('%', :termo, '%'))
          or lower(d.mensagem) like lower(concat('%', :termo, '%')))
      """)
  Page<Duvida> buscar(
      @Param("termo") String termo, @Param("status") DuvidaStatus status, Pageable pageable);
}
