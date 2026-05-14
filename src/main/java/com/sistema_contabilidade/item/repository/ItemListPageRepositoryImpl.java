package com.sistema_contabilidade.item.repository;

import com.sistema_contabilidade.item.dto.ItemListResponse;
import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
class ItemListPageRepositoryImpl implements ItemListPageRepository {

  @PersistenceContext private EntityManager entityManager;

  @Override
  public Page<ItemListResponse> findPageForList(ItemListPageQuery query, Pageable pageable) {
    CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
    List<ItemListResponse> content = findPageContent(query, pageable, criteriaBuilder);
    long total = countItems(query, criteriaBuilder);
    return new PageImpl<>(content, pageable, total);
  }

  private List<ItemListResponse> findPageContent(
      ItemListPageQuery query, Pageable pageable, CriteriaBuilder criteriaBuilder) {
    CriteriaQuery<ItemListResponse> criteriaQuery =
        criteriaBuilder.createQuery(ItemListResponse.class);
    Root<Item> root = criteriaQuery.from(Item.class);
    criteriaQuery.select(
        criteriaBuilder.construct(
            ItemListResponse.class,
            root.get("id"),
            root.get("valor"),
            root.get("data"),
            root.get("horarioCriacao"),
            root.get("caminhoArquivoPdf"),
            root.get("tipo"),
            root.get("roleNome"),
            root.get("descricao"),
            root.get("razaoSocialNome"),
            root.get("cnpjCpf"),
            root.get("observacao"),
            root.get("verificado"),
            buildHasAttachmentsExpression(criteriaBuilder, root)));
    criteriaQuery.where(buildPredicates(query, criteriaBuilder, root).toArray(Predicate[]::new));
    criteriaQuery.orderBy(buildSort(root, criteriaBuilder));

    TypedQuery<ItemListResponse> typedQuery = entityManager.createQuery(criteriaQuery);
    typedQuery.setFirstResult((int) pageable.getOffset());
    typedQuery.setMaxResults(pageable.getPageSize());
    return typedQuery.getResultList();
  }

  private long countItems(ItemListPageQuery query, CriteriaBuilder criteriaBuilder) {
    CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
    Root<Item> root = countQuery.from(Item.class);
    countQuery.select(criteriaBuilder.count(root));
    countQuery.where(buildPredicates(query, criteriaBuilder, root).toArray(Predicate[]::new));
    return entityManager.createQuery(countQuery).getSingleResult();
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
      predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("data"), dataInicio));
    }
    if (dataFim != null) {
      predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("data"), dataFim));
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
    if (razao == null || razao.isBlank()) {
      return;
    }
    predicates.add(
        criteriaBuilder.like(
            criteriaBuilder.upper(root.get("razaoSocialNome")),
            "%" + escapeLikePattern(razao.trim().toUpperCase(Locale.ROOT)) + "%",
            '\\'));
  }

  private List<Order> buildSort(Root<Item> root, CriteriaBuilder criteriaBuilder) {
    return List.of(
        criteriaBuilder.desc(root.get("horarioCriacao")), criteriaBuilder.desc(root.get("id")));
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
}
