package com.sistema_contabilidade.item.repository;

import com.sistema_contabilidade.home.dto.HomeLatestLaunchResponse;
import com.sistema_contabilidade.home.dto.HomeMonthlyBalanceRow;
import com.sistema_contabilidade.home.dto.HomeRevenueCategoryTotalRow;
import com.sistema_contabilidade.home.dto.HomeTypeTotalRow;
import com.sistema_contabilidade.item.dto.ItemListResponse;
import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.relatorio.dto.RelatorioItemDto;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemRepository extends JpaRepository<Item, UUID> {

  String ROLE_NAME_PARAM = "roleNome";

  @Query(
      """
      select new com.sistema_contabilidade.item.dto.ItemListResponse(
        i.id,
        i.valor,
        i.data,
        i.horarioCriacao,
        i.caminhoArquivoPdf,
        i.tipo,
        i.roleNome,
        i.descricao,
        i.razaoSocialNome,
        i.cnpjCpf,
        i.observacao,
        i.verificado,
        case
          when i.caminhoArquivoPdf is not null and trim(i.caminhoArquivoPdf) <> '' then true
          else false
        end
      )
      from Item i
      order by i.horarioCriacao desc
      """)
  List<ItemListResponse> findAllResumoOrderByHorarioCriacaoDesc();

  @Query(
      """
      select i
      from Item i
      where i.tipo = com.sistema_contabilidade.item.model.TipoItem.RECEITA
      order by i.horarioCriacao desc
      """)
  List<Item> findReceitasOrderByHorarioCriacaoDesc();

  @Query(
      """
      select new com.sistema_contabilidade.item.dto.ItemListResponse(
        i.id,
        i.valor,
        i.data,
        i.horarioCriacao,
        i.caminhoArquivoPdf,
        i.tipo,
        i.roleNome,
        i.descricao,
        i.razaoSocialNome,
        i.cnpjCpf,
        i.observacao,
        i.verificado,
        case
          when i.caminhoArquivoPdf is not null and trim(i.caminhoArquivoPdf) <> '' then true
          else false
        end
      )
      from Item i
      where i.roleNome in ?1
      order by i.horarioCriacao desc
      """)
  List<ItemListResponse> findResumoVisiveisPorRoleNomesOrderByHorarioCriacaoDesc(
      Set<String> roleNomes);

  @Query(
      """
      select i
      from Item i
      where i.tipo = com.sistema_contabilidade.item.model.TipoItem.RECEITA
        and i.roleNome in ?1
      order by i.horarioCriacao desc
      """)
  List<Item> findReceitasPorRoleNomesOrderByHorarioCriacaoDesc(Set<String> roleNomes);

  @Query(
      """
      select new com.sistema_contabilidade.item.dto.ItemListResponse(
        i.id,
        i.valor,
        i.data,
        i.horarioCriacao,
        i.caminhoArquivoPdf,
        i.tipo,
        i.roleNome,
        i.descricao,
        i.razaoSocialNome,
        i.cnpjCpf,
        i.observacao,
        i.verificado,
        case
          when i.caminhoArquivoPdf is not null and trim(i.caminhoArquivoPdf) <> '' then true
          else false
        end
      )
      from Item i
      where i.roleNome = :roleNome
      order by i.horarioCriacao desc
      """)
  List<ItemListResponse> findResumoVisiveisPorRoleNomeOrderByHorarioCriacaoDesc(
      @Param(ROLE_NAME_PARAM) String roleNome);

  @Query(
      """
      select i
      from Item i
      where i.tipo = com.sistema_contabilidade.item.model.TipoItem.RECEITA
        and i.roleNome = :roleNome
      order by i.horarioCriacao desc
      """)
  List<Item> findReceitasPorRoleNomeOrderByHorarioCriacaoDesc(
      @Param(ROLE_NAME_PARAM) String roleNome);

  @Query(
      """
      select new com.sistema_contabilidade.home.dto.HomeTypeTotalRow(
        i.tipo,
        coalesce(sum(i.valor), 0)
      )
      from Item i
      group by i.tipo
      """)
  List<HomeTypeTotalRow> findTypeTotals();

  @Query(
      """
      select new com.sistema_contabilidade.home.dto.HomeTypeTotalRow(
        i.tipo,
        coalesce(sum(i.valor), 0)
      )
      from Item i
      where i.roleNome = :roleNome
      group by i.tipo
      """)
  List<HomeTypeTotalRow> findTypeTotalsByRoleNome(@Param(ROLE_NAME_PARAM) String roleNome);

  @Query(
      """
      select new com.sistema_contabilidade.home.dto.HomeRevenueCategoryTotalRow(
        coalesce(i.descricao, ''),
        coalesce(sum(i.valor), 0)
      )
      from Item i
      where i.tipo = com.sistema_contabilidade.item.model.TipoItem.RECEITA
      group by i.descricao
      """)
  List<HomeRevenueCategoryTotalRow> findRevenueCategoryTotals();

  @Query(
      """
      select new com.sistema_contabilidade.home.dto.HomeRevenueCategoryTotalRow(
        coalesce(i.descricao, ''),
        coalesce(sum(i.valor), 0)
      )
      from Item i
      where i.tipo = com.sistema_contabilidade.item.model.TipoItem.RECEITA
        and i.roleNome = :roleNome
      group by i.descricao
      """)
  List<HomeRevenueCategoryTotalRow> findRevenueCategoryTotalsByRoleNome(
      @Param(ROLE_NAME_PARAM) String roleNome);

  @Query(
      """
      select new com.sistema_contabilidade.home.dto.HomeMonthlyBalanceRow(
        year(i.data),
        month(i.data),
        i.tipo,
        coalesce(sum(i.valor), 0)
      )
      from Item i
      where i.data >= :startDate
      group by year(i.data), month(i.data), i.tipo
      order by year(i.data), month(i.data)
      """)
  List<HomeMonthlyBalanceRow> findMonthlyBalanceRowsSince(
      @Param("startDate") java.time.LocalDate startDate);

  @Query(
      """
      select new com.sistema_contabilidade.home.dto.HomeMonthlyBalanceRow(
        year(i.data),
        month(i.data),
        i.tipo,
        coalesce(sum(i.valor), 0)
      )
      from Item i
      where i.roleNome = :roleNome
        and i.data >= :startDate
      group by year(i.data), month(i.data), i.tipo
      order by year(i.data), month(i.data)
      """)
  List<HomeMonthlyBalanceRow> findMonthlyBalanceRowsSinceByRoleNome(
      @Param(ROLE_NAME_PARAM) String roleNome, @Param("startDate") java.time.LocalDate startDate);

  @Query(
      """
      select new com.sistema_contabilidade.home.dto.HomeLatestLaunchResponse(
        i.horarioCriacao,
        i.data,
        i.valor,
        i.tipo,
        i.descricao,
        i.razaoSocialNome
      )
      from Item i
      order by i.horarioCriacao desc
      """)
  List<HomeLatestLaunchResponse> findLatestLaunches(Pageable pageable);

  @Query(
      """
      select new com.sistema_contabilidade.home.dto.HomeLatestLaunchResponse(
        i.horarioCriacao,
        i.data,
        i.valor,
        i.tipo,
        i.descricao,
        i.razaoSocialNome
      )
      from Item i
      where i.roleNome = :roleNome
      order by i.horarioCriacao desc
      """)
  List<HomeLatestLaunchResponse> findLatestLaunchesByRoleNome(
      @Param(ROLE_NAME_PARAM) String roleNome, Pageable pageable);

  @Query(
      """
      select new com.sistema_contabilidade.relatorio.dto.RelatorioItemDto(
        i.id,
        i.tipo,
        i.valor,
        i.data,
        i.horarioCriacao,
        i.descricao
      )
      from Item i
      order by i.data desc, i.horarioCriacao desc
      """)
  List<RelatorioItemDto> findAllRelatorioItensOrderByDataDescHorarioCriacaoDesc();

  @Query(
      """
      select new com.sistema_contabilidade.relatorio.dto.RelatorioItemDto(
        i.id,
        i.tipo,
        i.valor,
        i.data,
        i.horarioCriacao,
        i.descricao
      )
      from Item i
      where i.roleNome in ?1
      order by i.data desc, i.horarioCriacao desc
      """)
  List<RelatorioItemDto> findRelatorioItensByRoleNomesOrderByDataDescHorarioCriacaoDesc(
      Set<String> roleNomes);

  @Query(
      """
      select new com.sistema_contabilidade.relatorio.dto.RelatorioItemDto(
        i.id,
        i.tipo,
        i.valor,
        i.data,
        i.horarioCriacao,
        i.descricao
      )
      from Item i
      where i.roleNome = :roleNome
      order by i.data desc, i.horarioCriacao desc
      """)
  List<RelatorioItemDto> findRelatorioItensByRoleNomeOrderByDataDescHorarioCriacaoDesc(
      @Param(ROLE_NAME_PARAM) String roleNome);

  @Query(
      """
      select distinct i
      from Item i
      left join fetch i.arquivos
      where i.roleNome in ?1
      """)
  List<Item> findAllVisiveisPorRoleNomes(Set<String> roleNomes);

  @Query(
      """
      select distinct i
      from Item i
      left join fetch i.arquivos
      where i.roleNome = :roleNome
      """)
  List<Item> findAllVisiveisPorRoleNome(@Param(ROLE_NAME_PARAM) String roleNome);

  @Query(
      """
      select distinct i
      from Item i
      left join fetch i.criadoPor
      left join fetch i.arquivos
      where i.id = :id
      """)
  Optional<Item> findByIdComCriadorERoles(@Param("id") UUID id);

  List<Item> findByTipoAndRoleNome(TipoItem tipo, String roleNome);

  List<Item> findByTipoAndRoleNomeAndIdNot(TipoItem tipo, String roleNome, UUID id);

  @Query(
      """
      select count(i)
      from Item i
      where function(
              'replace',
              function(
                  'replace',
                  function('replace', function('replace', coalesce(i.cnpjCpf, ''), '.', ''), '-', ''),
                  '/',
                  ''),
              ' ',
              '')
          = :documento
      """)
  long countByDocumentoNormalizado(@Param("documento") String documento);

  @Query(
      """
      select count(i)
      from Item i
      where i.id <> :id
        and function(
                'replace',
                function(
                    'replace',
                    function('replace', function('replace', coalesce(i.cnpjCpf, ''), '.', ''), '-', ''),
                    '/',
                    ''),
                ' ',
                '')
            = :documento
      """)
  long countByDocumentoNormalizadoAndIdNot(
      @Param("documento") String documento, @Param("id") UUID id);
}
