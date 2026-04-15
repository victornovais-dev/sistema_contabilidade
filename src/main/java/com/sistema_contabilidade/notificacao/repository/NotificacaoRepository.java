package com.sistema_contabilidade.notificacao.repository;

import com.sistema_contabilidade.notificacao.dto.NotificacaoListResponse;
import com.sistema_contabilidade.notificacao.model.Notificacao;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

  @Query(
      """
      select new com.sistema_contabilidade.notificacao.dto.NotificacaoListResponse(
          n.id,
          n.itemId,
          n.roleNome,
          n.descricao,
          n.razaoSocialNome,
          n.valor,
          n.criadoEm)
      from Notificacao n
      order by n.criadoEm desc
      """)
  List<NotificacaoListResponse> findAllResumoOrderByCriadoEmDesc();

  @Query(
      """
      select new com.sistema_contabilidade.notificacao.dto.NotificacaoListResponse(
          n.id,
          n.itemId,
          n.roleNome,
          n.descricao,
          n.razaoSocialNome,
          n.valor,
          n.criadoEm)
      from Notificacao n
      where upper(trim(n.roleNome)) = :roleNome
      order by n.criadoEm desc
      """)
  List<NotificacaoListResponse> findResumoByRoleNomeOrderByCriadoEmDesc(
      @Param("roleNome") String roleNome);

  @Query(
      """
      select new com.sistema_contabilidade.notificacao.dto.NotificacaoListResponse(
          n.id,
          n.itemId,
          n.roleNome,
          n.descricao,
          n.razaoSocialNome,
          n.valor,
          n.criadoEm)
      from Notificacao n
      where upper(trim(n.roleNome)) in :roleNomes
      order by n.criadoEm desc
      """)
  List<NotificacaoListResponse> findResumoByRoleNomesOrderByCriadoEmDesc(
      @Param("roleNomes") Set<String> roleNomes);
}
