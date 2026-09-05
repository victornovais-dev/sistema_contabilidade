package com.sistema_contabilidade.item.service;

import com.sistema_contabilidade.item.dto.ItemAuditoriaAlteracaoResponse;
import com.sistema_contabilidade.item.dto.ItemAuditoriaResponse;
import com.sistema_contabilidade.item.model.ItemAuditoria;
import com.sistema_contabilidade.item.model.ItemAuditoriaAcao;
import com.sistema_contabilidade.item.repository.ItemAuditoriaRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ItemAuditHistoryService {

  private static final String CAMPO_ARQUIVOS = "arquivos";

  private static final Set<String> CAMPOS_EXPOSTOS =
      Set.of(
          "valor",
          "data",
          "horarioCriacao",
          "tipo",
          "role",
          "descricao",
          "tipoDocumento",
          "numeroDocumento",
          "razaoSocialNome",
          "cnpjCpf",
          "observacao",
          "verificado",
          CAMPO_ARQUIVOS,
          "pagamento");

  private final ItemAuditoriaRepository auditoriaRepository;
  private final ObjectMapper objectMapper;

  public ItemAuditHistoryService(
      ItemAuditoriaRepository auditoriaRepository, ObjectMapper objectMapper) {
    this.auditoriaRepository = Objects.requireNonNull(auditoriaRepository, "auditoriaRepository");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  @Transactional(readOnly = true)
  public List<ItemAuditoriaResponse> listarPorItemId(UUID itemId) {
    Objects.requireNonNull(itemId, "itemId");
    return auditoriaRepository.findByItemIdOrderByOcorridoEmDescIdDesc(itemId).stream()
        .map(this::toResponse)
        .toList();
  }

  private ItemAuditoriaResponse toResponse(ItemAuditoria auditoria) {
    return new ItemAuditoriaResponse(
        auditoria.getId(),
        auditoria.getAcao(),
        auditoria.getAtorNome(),
        auditoria.getOcorridoEm(),
        alterarCampos(auditoria));
  }

  private Map<String, ItemAuditoriaAlteracaoResponse> alterarCampos(ItemAuditoria auditoria) {
    if (auditoria.getAcao() == ItemAuditoriaAcao.CRIADO) {
      return alterarCamposDoSnapshot(auditoria.getSnapshotItem(), true);
    }
    if (auditoria.getAcao() == ItemAuditoriaAcao.EXCLUIDO) {
      return alterarCamposDoSnapshot(auditoria.getSnapshotItem(), false);
    }
    return alterarCamposDoDiff(auditoria.getAlteracoes());
  }

  private Map<String, ItemAuditoriaAlteracaoResponse> alterarCamposDoSnapshot(
      String snapshotItem, boolean criado) {
    JsonNode snapshot = lerObjeto(snapshotItem, "snapshot da auditoria");
    Map<String, ItemAuditoriaAlteracaoResponse> alteracoes = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> campo : snapshot.properties()) {
      if (!CAMPOS_EXPOSTOS.contains(campo.getKey())) {
        continue;
      }
      JsonNode valor = sanitizarValor(campo.getKey(), campo.getValue());
      alteracoes.put(
          campo.getKey(),
          new ItemAuditoriaAlteracaoResponse(
              criado ? objectMapper.nullNode() : valor, criado ? valor : objectMapper.nullNode()));
    }
    return alteracoes;
  }

  private Map<String, ItemAuditoriaAlteracaoResponse> alterarCamposDoDiff(String alteracoesJson) {
    JsonNode diff = lerObjeto(alteracoesJson, "alteracoes da auditoria");
    Map<String, ItemAuditoriaAlteracaoResponse> alteracoes = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> campo : diff.properties()) {
      if (!CAMPOS_EXPOSTOS.contains(campo.getKey()) || !campo.getValue().isObject()) {
        continue;
      }
      JsonNode antes = campo.getValue().get("antes");
      JsonNode depois = campo.getValue().get("depois");
      alteracoes.put(
          campo.getKey(),
          new ItemAuditoriaAlteracaoResponse(
              sanitizarValor(campo.getKey(), antes), sanitizarValor(campo.getKey(), depois)));
    }
    return alteracoes;
  }

  private JsonNode sanitizarValor(String campo, JsonNode valor) {
    if (valor == null || valor.isNull()) {
      return objectMapper.nullNode();
    }
    return switch (campo) {
      case CAMPO_ARQUIVOS -> sanitizarArquivos(valor);
      case "pagamento" -> sanitizarPagamento(valor);
      default -> valor.deepCopy();
    };
  }

  private ArrayNode sanitizarArquivos(JsonNode arquivos) {
    ArrayNode resposta = objectMapper.createArrayNode();
    if (!arquivos.isArray()) {
      return resposta;
    }
    for (JsonNode arquivo : arquivos) {
      if (!arquivo.isObject()) {
        continue;
      }
      ObjectNode arquivoResposta = resposta.addObject();
      copiarCampo(arquivoResposta, arquivo, "nome");
      copiarCampo(arquivoResposta, arquivo, "origem");
    }
    return resposta;
  }

  private JsonNode sanitizarPagamento(JsonNode pagamento) {
    if (!pagamento.isObject()) {
      return objectMapper.nullNode();
    }
    ObjectNode resposta = objectMapper.createObjectNode();
    copiarCampo(resposta, pagamento, "forma");
    JsonNode parcelas = pagamento.get("parcelas");
    if (parcelas == null || !parcelas.isArray()) {
      return resposta;
    }
    ArrayNode parcelasResposta = resposta.putArray("parcelas");
    for (JsonNode parcela : parcelas) {
      if (!parcela.isObject()) {
        continue;
      }
      ObjectNode parcelaResposta = parcelasResposta.addObject();
      copiarCampo(parcelaResposta, parcela, "numero");
      copiarCampo(parcelaResposta, parcela, "valor");
      copiarCampo(parcelaResposta, parcela, "paga");
      copiarCampo(parcelaResposta, parcela, "contaOrigem");
      parcelaResposta.set(CAMPO_ARQUIVOS, sanitizarArquivos(parcela.get(CAMPO_ARQUIVOS)));
    }
    return resposta;
  }

  private void copiarCampo(ObjectNode destino, JsonNode origem, String campo) {
    JsonNode valor = origem.get(campo);
    if (valor != null) {
      destino.set(campo, valor.deepCopy());
    }
  }

  private JsonNode lerObjeto(String conteudo, String descricao) {
    try {
      JsonNode json = objectMapper.readTree(conteudo);
      if (json == null || !json.isObject()) {
        throw new IllegalStateException("Formato invalido de " + descricao);
      }
      return json;
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao ler " + descricao, exception);
    }
  }
}
