package com.sistema_contabilidade.item.service;

import com.sistema_contabilidade.item.config.ItemDescricaoCatalog;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemDescricaoRepository;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemDescricaoService {

  private static final String OUTRAS_DESPESAS = "Outras despesas";
  private static final Set<String> DESCRICOES_RECEITA_REMOVIDAS = Set.of("CONTA FEFEC");

  private final ItemDescricaoRepository itemDescricaoRepository;

  @Cacheable(value = ItemDescricaoCatalog.ITEM_DESCRICOES_CACHE, key = "#p0.name()")
  @Transactional(readOnly = true)
  public List<String> listarDescricoesPorTipo(TipoItem tipo) {
    if (tipo == null) {
      return List.of();
    }
    try {
      List<String> descricoesBanco =
          itemDescricaoRepository.findByTipoOrderByOrdemAscNomeAsc(tipo).stream()
              .map(itemDescricao -> itemDescricao.getNome())
              .toList();
      if (!descricoesBanco.isEmpty()) {
        return ordenarDescricoes(descricoesBanco, tipo);
      }
    } catch (RuntimeException exception) {
      if (log.isWarnEnabled()) {
        log.warn(
            "Falha ao carregar descricoes do banco para tipo {}. Usando catalogo padrao.",
            tipo,
            exception);
      }
    }
    return ordenarDescricoes(
        ItemDescricaoCatalog.defaultDescriptions().stream()
            .filter(seed -> seed.tipo() == tipo)
            .map(seed -> seed.nome())
            .toList(),
        tipo);
  }

  private List<String> ordenarDescricoes(List<String> descricoes, TipoItem tipo) {
    return descricoes.stream()
        .filter(descricao -> descricaoEstaDisponivel(tipo, descricao))
        .sorted(descricaoComparator(tipo))
        .toList();
  }

  private boolean descricaoEstaDisponivel(TipoItem tipo, String descricao) {
    if (tipo != TipoItem.RECEITA) {
      return true;
    }
    return !DESCRICOES_RECEITA_REMOVIDAS.contains(normalizarDescricao(descricao));
  }

  private Comparator<String> descricaoComparator(TipoItem tipo) {
    return Comparator.comparing((String descricao) -> deveIrParaOFim(tipo, descricao))
        .thenComparing(ItemDescricaoService::normalizarParaOrdenacao);
  }

  private boolean deveIrParaOFim(TipoItem tipo, String descricao) {
    return tipo == TipoItem.DESPESA && OUTRAS_DESPESAS.equalsIgnoreCase(String.valueOf(descricao));
  }

  private static String normalizarParaOrdenacao(String valor) {
    String texto = String.valueOf(valor).trim().toLowerCase(Locale.ROOT);
    return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
  }

  private static String normalizarDescricao(String valor) {
    return String.valueOf(valor).trim().toUpperCase(Locale.ROOT);
  }
}
