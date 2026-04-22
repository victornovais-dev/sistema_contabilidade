package com.sistema_contabilidade.notificacao.repository;

import com.sistema_contabilidade.notificacao.dto.NotificacaoListResponse;
import com.sistema_contabilidade.notificacao.model.Notificacao;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

  Optional<Notificacao> findFirstByItemId(UUID itemId);

  void deleteByItemId(UUID itemId);

  @Modifying
  @Query(
      value =
          """
          delete n
          from notificacoes n
          left join itens i on i.id = n.item_id
          where i.id is null or i.tipo <> 'RECEITA'
          """,
      nativeQuery = true)
  int deleteOrfasOuInvalidas();

  @Query(
      """
      select new com.sistema_contabilidade.notificacao.dto.NotificacaoListResponse(
          n.id,
          n.itemId,
          n.roleNome,
          n.descricao,
          n.razaoSocialNome,
          n.valor,
          n.criadoEm,
          n.limpa,
          i.verificado)
      from Notificacao n, Item i
      where i.id = n.itemId
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
          n.criadoEm,
          n.limpa,
          i.verificado)
      from Notificacao n, Item i
      where i.id = n.itemId
        and upper(trim(n.roleNome)) = :roleNome
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
          n.criadoEm,
          n.limpa,
          i.verificado)
      from Notificacao n, Item i
      where i.id = n.itemId
        and upper(trim(n.roleNome)) in :roleNomes
      order by n.criadoEm desc
      """)
  List<NotificacaoListResponse> findResumoByRoleNomesOrderByCriadoEmDesc(
      @Param("roleNomes") Set<String> roleNomes);
}
