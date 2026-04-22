package com.sistema_contabilidade.item.service;

import com.sistema_contabilidade.item.dto.ItemUpsertRequest;
import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemRepository;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ItemExpenseLimitService {

  private static final Set<String> CATEGORIAS_COMBUSTIVEL = Set.of("COMBUSTIVEIS E LUBRIFICANTES");
  private static final Set<String> CATEGORIAS_ALIMENTACAO = Set.of("ALIMENTACAO");
  private static final Set<String> CATEGORIAS_LOCACAO =
      Set.of("ALUGUEL DE IMOVEIS", "ALUGUEL DE VEICULOS");

  private final ItemRepository itemRepository;

  public void validarLimiteDespesa(
      ItemUpsertRequest request, String roleNome, UUID itemIdIgnorado) {
    if (request == null || request.tipo() != TipoItem.DESPESA) {
      return;
    }

    GrupoLimite grupo = GrupoLimite.fromDescricao(request.descricao());
    if (grupo == null) {
      return;
    }

    String roleNormalizada = ItemAccessUtils.normalizarRole(roleNome);
    if (roleNormalizada == null) {
      return;
    }

    List<Item> despesasExistentes =
        itemIdIgnorado == null
            ? itemRepository.findByTipoAndRoleNome(TipoItem.DESPESA, roleNormalizada)
            : itemRepository.findByTipoAndRoleNomeAndIdNot(
                TipoItem.DESPESA, roleNormalizada, itemIdIgnorado);

    BigDecimal valorNovo = request.valor() == null ? BigDecimal.ZERO : request.valor();
    BigDecimal totalDespesasBase = somarValores(despesasExistentes);
    BigDecimal totalCategoriaBase =
        despesasExistentes.stream()
            .filter(item -> grupo.corresponde(item.getDescricao()))
            .map(Item::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalDespesasProposto = totalDespesasBase.add(valorNovo);
    BigDecimal totalCategoriaProposto = totalCategoriaBase.add(valorNovo);
    BigDecimal limitePermitido = totalDespesasProposto.multiply(grupo.percentualMaximo());

    if (totalCategoriaProposto.compareTo(limitePermitido) > 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, grupo.mensagemLimiteExcedido());
    }
  }

  private BigDecimal somarValores(List<Item> itens) {
    return itens.stream()
        .map(Item::getValor)
        .filter(java.util.Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static String normalizarDescricao(String descricao) {
    if (descricao == null || descricao.isBlank()) {
      return "";
    }
    String normalizada = Normalizer.normalize(descricao.trim(), Normalizer.Form.NFD);
    normalizada = normalizada.replaceAll("\\p{M}+", "");
    return normalizada.toUpperCase(Locale.ROOT);
  }

  private enum GrupoLimite {
    COMBUSTIVEL(
        CATEGORIAS_COMBUSTIVEL,
        new BigDecimal("0.10"),
        "Nao e permitido adicionar esta despesa. Combustivel e lubrificantes pode representar no maximo 10% do total de despesas."),
    ALIMENTACAO(
        CATEGORIAS_ALIMENTACAO,
        new BigDecimal("0.10"),
        "Nao e permitido adicionar esta despesa. Alimentacao pode representar no maximo 10% do total de despesas."),
    LOCACAO(
        CATEGORIAS_LOCACAO,
        new BigDecimal("0.20"),
        "Nao e permitido adicionar esta despesa. Locacao pode representar no maximo 20% do total de despesas.");

    private final Set<String> categorias;
    private final BigDecimal percentualMaximoPermitido;
    private final String mensagemBloqueio;

    GrupoLimite(
        Set<String> categorias, BigDecimal percentualMaximo, String mensagemLimiteExcedido) {
      this.categorias = categorias;
      this.percentualMaximoPermitido = percentualMaximo;
      this.mensagemBloqueio = mensagemLimiteExcedido;
    }

    private boolean corresponde(String descricao) {
      return categorias.contains(normalizarDescricao(descricao));
    }

    private BigDecimal percentualMaximo() {
      return percentualMaximoPermitido;
    }

    private String mensagemLimiteExcedido() {
      return mensagemBloqueio;
    }

    private static GrupoLimite fromDescricao(String descricao) {
      String descricaoNormalizada = normalizarDescricao(descricao);
      for (GrupoLimite grupo : values()) {
        if (grupo.categorias.contains(descricaoNormalizada)) {
          return grupo;
        }
      }
      return null;
    }
  }
}
