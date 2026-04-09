package com.sistema_contabilidade.item.repository;

import com.sistema_contabilidade.home.dto.HomeLatestLaunchResponse;
import com.sistema_contabilidade.home.dto.HomeMonthlyBalanceRow;
import com.sistema_contabilidade.home.dto.HomeTypeTotalRow;
import com.sistema_contabilidade.item.dto.ItemListResponse;
import com.sistema_contabilidade.item.model.Item;
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
        case
          when i.caminhoArquivoPdf is not null and trim(i.caminhoArquivoPdf) <> '' then true
          else false
        end
      )
      from Item i
      where i.roleNome in :roleNomes
      order by i.horarioCriacao desc
      """)
  List<ItemListResponse> findResumoVisiveisPorRoleNomesOrderByHorarioCriacaoDesc(
      @Param("roleNomes") Set<String> roleNomes);

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
      where i.roleNome in :roleNomes
      order by i.data desc, i.horarioCriacao desc
      """)
  List<RelatorioItemDto> findRelatorioItensByRoleNomesOrderByDataDescHorarioCriacaoDesc(
      @Param("roleNomes") Set<String> roleNomes);

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
      where i.roleNome in :roleNomes
      """)
  List<Item> findAllVisiveisPorRoleNomes(@Param("roleNomes") Set<String> roleNomes);

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
      select i
      from Item i
      left join fetch i.criadoPor u
      left join fetch u.roles
      left join fetch i.arquivos
      where i.id = :id
      """)
  Optional<Item> findByIdComCriadorERoles(@Param("id") UUID id);
}
