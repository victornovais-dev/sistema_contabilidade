package com.sistema_contabilidade.item.repository;

import com.sistema_contabilidade.common.util.SearchTextNormalizer;
import com.sistema_contabilidade.item.config.ItemRazaoSocialSearchDatabaseSupport;
import com.sistema_contabilidade.item.dto.ItemListResponse;
import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ItemListPageRepositoryImpl implements ItemListPageRepository {

  private static final int FULLTEXT_MIN_TOKEN_LENGTH = 3;
  private static final String FIELD_DATA = "data";
  private static final String FIELD_ID = "id";
  private static final String FIELD_HORARIO_CRIACAO = "horarioCriacao";

  @PersistenceContext private EntityManager entityManager;
  private final ObjectProvider<ItemRazaoSocialSearchDatabaseSupport> databaseSupportProvider;

  @Override
  public Slice<ItemListResponse> findPageForList(ItemListPageQuery query, Pageable pageable) {
    if (shouldUseFullText(query)) {
      return findPageContentWithFullText(query, pageable);
    }

    CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
    return findPageContent(query, pageable, criteriaBuilder);
  }

  private boolean shouldUseFullText(ItemListPageQuery query) {
    ItemRazaoSocialSearchDatabaseSupport databaseSupport = databaseSupportProvider.getIfAvailable();
    return query != null
        && databaseSupport != null
        && databaseSupport.supportsFullTextSearch()
        && SearchTextNormalizer.allTokensHaveMinLength(query.razao(), FULLTEXT_MIN_TOKEN_LENGTH);
  }

  private Slice<ItemListResponse> findPageContent(
      ItemListPageQuery query, Pageable pageable, CriteriaBuilder criteriaBuilder) {
    CriteriaQuery<ItemListResponse> criteriaQuery =
        criteriaBuilder.createQuery(ItemListResponse.class);
    Root<Item> root = criteriaQuery.from(Item.class);
    applyProjection(criteriaQuery, criteriaBuilder, root);
    criteriaQuery.where(buildPredicates(query, criteriaBuilder, root).toArray(Predicate[]::new));
    criteriaQuery.orderBy(buildSort(root, criteriaBuilder));

    TypedQuery<ItemListResponse> typedQuery = entityManager.createQuery(criteriaQuery);
    typedQuery.setFirstResult((int) pageable.getOffset());
    typedQuery.setMaxResults(pageable.getPageSize() + 1);
    List<ItemListResponse> rows = typedQuery.getResultList();
    boolean hasNext = rows.size() > pageable.getPageSize();
    if (hasNext) {
      rows = new ArrayList<>(rows.subList(0, pageable.getPageSize()));
    }
    return new SliceImpl<>(rows, pageable, hasNext);
  }

  private Slice<ItemListResponse> findPageContentWithFullText(
      ItemListPageQuery query, Pageable pageable) {
    List<UUID> ids = findIdsWithFullText(query, pageable);
    boolean hasNext = ids.size() > pageable.getPageSize();
    if (hasNext) {
      ids = new ArrayList<>(ids.subList(0, pageable.getPageSize()));
    }
    if (ids.isEmpty()) {
      return new SliceImpl<>(List.of(), pageable, false);
    }

    CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
    CriteriaQuery<ItemListResponse> criteriaQuery =
        criteriaBuilder.createQuery(ItemListResponse.class);
    Root<Item> root = criteriaQuery.from(Item.class);
    applyProjection(criteriaQuery, criteriaBuilder, root);
    criteriaQuery.where(root.get(FIELD_ID).in(ids));
    criteriaQuery.orderBy(buildSort(root, criteriaBuilder));

    List<ItemListResponse> rows = entityManager.createQuery(criteriaQuery).getResultList();
    return new SliceImpl<>(rows, pageable, hasNext);
  }

  private List<UUID> findIdsWithFullText(ItemListPageQuery query, Pageable pageable) {
    StringBuilder sql = new StringBuilder("select i.id from itens i where 1 = 1");
    Map<Integer, Object> parameters = new LinkedHashMap<>();
    int nextParameterIndex = 1;

    if (query.roleNomes() != null && !query.roleNomes().isEmpty()) {
      sql.append(" and i.role_nome in (");
      int startIndex = nextParameterIndex;
      for (String roleNome : query.roleNomes()) {
        if (nextParameterIndex > startIndex) {
          sql.append(", ");
        }
        sql.append("?").append(nextParameterIndex);
        parameters.put(nextParameterIndex, roleNome);
        nextParameterIndex++;
      }
      sql.append(")");
    }

    if (query.tipo() != null) {
      sql.append(" and i.tipo = ?").append(nextParameterIndex);
      parameters.put(nextParameterIndex, query.tipo().name());
      nextParameterIndex++;
    }

    if (query.dataInicio() != null) {
      sql.append(" and i.data >= ?").append(nextParameterIndex);
      parameters.put(nextParameterIndex, query.dataInicio());
      nextParameterIndex++;
    }

    if (query.dataFim() != null) {
      sql.append(" and i.data <= ?").append(nextParameterIndex);
      parameters.put(nextParameterIndex, query.dataFim());
      nextParameterIndex++;
    }

    if (query.descricao() != null && !query.descricao().isBlank()) {
      sql.append(" and upper(trim(i.descricao)) = ?").append(nextParameterIndex);
      parameters.put(nextParameterIndex, query.descricao().trim().toUpperCase(Locale.ROOT));
      nextParameterIndex++;
    }

    String booleanQuery =
        SearchTextNormalizer.toBooleanPrefixQuery(query.razao(), FULLTEXT_MIN_TOKEN_LENGTH);
    if (booleanQuery != null) {
      sql.append(" and match(i.razao_social_busca) against (?")
          .append(nextParameterIndex)
          .append(" in boolean mode)");
      parameters.put(nextParameterIndex, booleanQuery);
      nextParameterIndex++;
    }

    sql.append(" order by i.horario_criacao desc, i.id desc");
    sql.append(" limit ?").append(nextParameterIndex);
    parameters.put(nextParameterIndex, pageable.getPageSize() + 1);
    nextParameterIndex++;
    sql.append(" offset ?").append(nextParameterIndex);
    parameters.put(nextParameterIndex, pageable.getOffset());

    Query nativeQuery = entityManager.createNativeQuery(sql.toString());
    parameters.forEach(nativeQuery::setParameter);
    @SuppressWarnings("unchecked")
    List<Object> rawIds = nativeQuery.getResultList();
    return rawIds.stream().map(this::toUuid).toList();
  }

  private void applyProjection(
      CriteriaQuery<ItemListResponse> criteriaQuery,
      CriteriaBuilder criteriaBuilder,
      Root<Item> root) {
    criteriaQuery.select(
        criteriaBuilder.construct(
            ItemListResponse.class,
            root.get(FIELD_ID),
            root.get("valor"),
            root.get(FIELD_DATA),
            root.get(FIELD_HORARIO_CRIACAO),
            root.get("caminhoArquivoPdf"),
            root.get("tipo"),
            root.get("roleNome"),
            root.get("descricao"),
            root.get("razaoSocialNome"),
            root.get("cnpjCpf"),
            root.get("observacao"),
            root.get("verificado"),
            buildHasAttachmentsExpression(criteriaBuilder, root)));
  }

  private List<Predicate> buildPredicates(
      ItemListPageQuery query, CriteriaBuilder criteriaBuilder, Root<Item> root) {
    List<Predicate> predicates = new ArrayList<>();
    addRolePredicate(query.roleNomes(), root, predicates);
    addTipoPredicate(query.tipo(), criteriaBuilder, root, predicates);
    addDateRangePredicate(query.dataInicio(), query.dataFim(), criteriaBuilder, root, predicates);
    addDescricaoPredicate(query.descricao(), criteriaBuilder, root, predicates);
    addRazaoPredicate(query.razao(), criteriaBuilder, root, predicates);
    return predicates;
  }

  private void addRolePredicate(
      Set<String> roleNomes, Root<Item> root, List<Predicate> predicates) {
    if (roleNomes == null || roleNomes.isEmpty()) {
      return;
    }
    predicates.add(root.get("roleNome").in(normalizeRoles(roleNomes)));
  }

  private void addTipoPredicate(
      TipoItem tipo, CriteriaBuilder criteriaBuilder, Root<Item> root, List<Predicate> predicates) {
    if (tipo == null) {
      return;
    }
    predicates.add(criteriaBuilder.equal(root.get("tipo"), tipo));
  }

  private void addDateRangePredicate(
      LocalDate dataInicio,
      LocalDate dataFim,
      CriteriaBuilder criteriaBuilder,
      Root<Item> root,
      List<Predicate> predicates) {
    if (dataInicio != null) {
      predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get(FIELD_DATA), dataInicio));
    }
    if (dataFim != null) {
      predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get(FIELD_DATA), dataFim));
    }
  }

  private void addDescricaoPredicate(
      String descricao,
      CriteriaBuilder criteriaBuilder,
      Root<Item> root,
      List<Predicate> predicates) {
    if (descricao == null || descricao.isBlank()) {
      return;
    }
    predicates.add(
        criteriaBuilder.equal(
            criteriaBuilder.upper(criteriaBuilder.trim(root.get("descricao"))),
            descricao.trim().toUpperCase(Locale.ROOT)));
  }

  private void addRazaoPredicate(
      String razao, CriteriaBuilder criteriaBuilder, Root<Item> root, List<Predicate> predicates) {
    List<String> tokens = SearchTextNormalizer.tokenize(razao);
    if (tokens.isEmpty()) {
      return;
    }

    for (String token : tokens) {
      String escapedToken = escapeLikePattern(token);
      predicates.add(
          criteriaBuilder.or(
              criteriaBuilder.like(root.get("razaoSocialBusca"), escapedToken + "%", '\\'),
              criteriaBuilder.like(root.get("razaoSocialBusca"), "% " + escapedToken + "%", '\\')));
    }
  }

  private List<Order> buildSort(Root<Item> root, CriteriaBuilder criteriaBuilder) {
    return List.of(
        criteriaBuilder.desc(root.get(FIELD_HORARIO_CRIACAO)),
        criteriaBuilder.desc(root.get(FIELD_ID)));
  }

  private Expression<Boolean> buildHasAttachmentsExpression(
      CriteriaBuilder criteriaBuilder, Root<Item> root) {
    Path<String> caminhoArquivoPdf = root.get("caminhoArquivoPdf");
    return criteriaBuilder
        .<Boolean>selectCase()
        .when(
            criteriaBuilder.and(
                criteriaBuilder.isNotNull(caminhoArquivoPdf),
                criteriaBuilder.notEqual(criteriaBuilder.trim(caminhoArquivoPdf), "")),
            true)
        .otherwise(false);
  }

  private Set<String> normalizeRoles(Set<String> roleNomes) {
    return roleNomes.stream()
        .filter(java.util.Objects::nonNull)
        .map(role -> role.trim().toUpperCase(Locale.ROOT))
        .filter(role -> !role.isBlank())
        .collect(java.util.stream.Collectors.toSet());
  }

  private String escapeLikePattern(String input) {
    return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private UUID toUuid(Object value) {
    if (value instanceof UUID uuid) {
      return uuid;
    }
    if (value instanceof String stringValue) {
      return UUID.fromString(stringValue);
    }
    if (value instanceof byte[] bytes && bytes.length == 16) {
      java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
      return new UUID(buffer.getLong(), buffer.getLong());
    }
    throw new IllegalStateException("Formato de UUID nao suportado para busca FULLTEXT: " + value);
  }
}
