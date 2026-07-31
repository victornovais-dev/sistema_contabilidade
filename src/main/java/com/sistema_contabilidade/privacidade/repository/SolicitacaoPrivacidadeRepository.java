package com.sistema_contabilidade.privacidade.repository;

import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidade;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeStatus;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeTipo;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolicitacaoPrivacidadeRepository
    extends JpaRepository<SolicitacaoPrivacidade, UUID> {

  boolean existsByProtocolo(String protocolo);

  Optional<SolicitacaoPrivacidade> findByProtocoloAndEmailBlindIndex(
      String protocolo, String emailBlindIndex);

  @EntityGraph(attributePaths = "eventos")
  Optional<SolicitacaoPrivacidade> findWithEventosByProtocolo(String protocolo);

  @Query(
      """
      select s from SolicitacaoPrivacidade s
      where (:status is null or s.status = :status)
        and (:tipo is null or s.tipo = :tipo)
        and (:termo = '' or lower(s.protocolo) like lower(concat('%', :termo, '%')))
      """)
  Page<SolicitacaoPrivacidade> buscar(
      @Param("termo") String termo,
      @Param("status") SolicitacaoPrivacidadeStatus status,
      @Param("tipo") SolicitacaoPrivacidadeTipo tipo,
      Pageable pageable);

  long countByStatusNotIn(Collection<SolicitacaoPrivacidadeStatus> status);

  long countByPrazoBeforeAndStatusNotIn(
      LocalDate prazo, Collection<SolicitacaoPrivacidadeStatus> status);

  long countByPrazoBetweenAndStatusNotIn(
      LocalDate inicio, LocalDate fim, Collection<SolicitacaoPrivacidadeStatus> status);

  long countByStatus(SolicitacaoPrivacidadeStatus status);
}
