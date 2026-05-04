package com.sistema_contabilidade.item.repository;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;

public final class ItemListSpecifications {

  private ItemListSpecifications() {}

  public static Specification<Item> forList(
      Set<String> roleNomes,
      TipoItem tipo,
      LocalDate dataInicio,
      LocalDate dataFim,
      String descricao,
      String razao) {
    return java.util.stream.Stream.of(
            withRoleNomes(roleNomes),
            withTipo(tipo),
            withDataInicio(dataInicio),
            withDataFim(dataFim),
            withDescricaoExata(descricao),
            withRazaoSocialLike(razao))
        .filter(java.util.Objects::nonNull)
        .reduce(Specification::and)
        .orElse(null);
  }

  private static Specification<Item> withRoleNomes(Set<String> roleNomes) {
    if (roleNomes == null || roleNomes.isEmpty()) {
      return null;
    }

    Set<String> roleNomesNormalizados =
        roleNomes.stream()
            .filter(java.util.Objects::nonNull)
            .map(role -> role.trim().toUpperCase(Locale.ROOT))
            .filter(role -> !role.isBlank())
            .collect(Collectors.toSet());
    if (roleNomesNormalizados.isEmpty()) {
      return null;
    }

    return (root, query, criteriaBuilder) -> root.get("roleNome").in(roleNomesNormalizados);
  }

  private static Specification<Item> withTipo(TipoItem tipo) {
    if (tipo == null) {
      return null;
    }

    return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("tipo"), tipo);
  }

  private static Specification<Item> withDataInicio(LocalDate dataInicio) {
    if (dataInicio == null) {
      return null;
    }

    return (root, query, criteriaBuilder) ->
        criteriaBuilder.greaterThanOrEqualTo(root.get("data"), dataInicio);
  }

  private static Specification<Item> withDataFim(LocalDate dataFim) {
    if (dataFim == null) {
      return null;
    }

    return (root, query, criteriaBuilder) ->
        criteriaBuilder.lessThanOrEqualTo(root.get("data"), dataFim);
  }

  private static Specification<Item> withDescricaoExata(String descricao) {
    if (descricao == null || descricao.isBlank()) {
      return null;
    }

    String descricaoNormalizada = descricao.trim().toUpperCase(Locale.ROOT);
    return (root, query, criteriaBuilder) ->
        criteriaBuilder.equal(
            criteriaBuilder.upper(criteriaBuilder.trim(root.get("descricao"))),
            descricaoNormalizada);
  }

  private static Specification<Item> withRazaoSocialLike(String razao) {
    if (razao == null || razao.isBlank()) {
      return null;
    }

    String escaped = escapeLikePattern(razao.trim().toUpperCase(Locale.ROOT));
    return (root, query, criteriaBuilder) ->
        criteriaBuilder.like(
            criteriaBuilder.upper(root.get("razaoSocialNome")), "%" + escaped + "%", '\\');
  }

  private static String escapeLikePattern(String input) {
    return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
