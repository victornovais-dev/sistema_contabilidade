package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.ItemParcelaPagamento;
import com.sistema_contabilidade.item.model.TipoItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record ItemResponse(
    UUID id,
    BigDecimal valor,
    LocalDate data,
    LocalDateTime horarioCriacao,
    String caminhoArquivoPdf,
    TipoItem tipo,
    String role,
    String descricao,
    String tipoDocumento,
    String numeroDocumento,
    String razaoSocialNome,
    String cnpjCpf,
    String observacao,
    boolean verificado,
    List<String> arquivosPdf,
    ItemPagamentoResponse pagamento) {

  public ItemResponse {
    arquivosPdf = arquivosPdf == null ? List.of() : List.copyOf(arquivosPdf);
  }

  @Override
  public List<String> arquivosPdf() {
    return arquivosPdf == null ? List.of() : List.copyOf(arquivosPdf);
  }

  public static ItemResponse from(Item item) {
    return new ItemResponse(
        item.getId(),
        item.getValor(),
        item.getData(),
        item.getHorarioCriacao(),
        item.getCaminhoArquivoPdf(),
        item.getTipo(),
        item.getRoleNome(),
        item.getDescricao(),
        item.getTipoDocumento(),
        item.getNumeroDocumento(),
        item.getRazaoSocialNome(),
        item.getCnpjCpf(),
        item.getObservacao(),
        item.isVerificado(),
        item.getArquivos().stream().map(arquivo -> arquivo.getCaminhoArquivoPdf()).toList(),
        mapPagamento(item));
  }

  private static ItemPagamentoResponse mapPagamento(Item item) {
    List<ItemParcelaPagamento> parcelas = item.getParcelasPagamento();
    if (parcelas == null || parcelas.isEmpty()) {
      return null;
    }
    List<ItemPagamentoParcelaResponse> parcelasResponse =
        parcelas.stream()
            .sorted(Comparator.comparing(ItemParcelaPagamento::getNumero))
            .map(ItemResponse::mapParcelaPagamento)
            .toList();
    BigDecimal totalPago =
        parcelasResponse.stream()
            .filter(ItemPagamentoParcelaResponse::paga)
            .map(ItemPagamentoParcelaResponse::valorParcela)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new ItemPagamentoResponse(
        item.getFormaPagamento(), parcelasResponse.size(), totalPago, parcelasResponse);
  }

  private static ItemPagamentoParcelaResponse mapParcelaPagamento(ItemParcelaPagamento parcela) {
    List<ItemPagamentoArquivoResponse> arquivos =
        parcela.getArquivosComprovantes().stream()
            .map(
                arquivo ->
                    new ItemPagamentoArquivoResponse(
                        arquivo.getId(), extractFileName(arquivo.getCaminhoArquivoPdf())))
            .toList();
    if (arquivos.isEmpty() && parcela.getCaminhoArquivoPdf() != null) {
      arquivos =
          List.of(
              new ItemPagamentoArquivoResponse(
                  null, extractFileName(parcela.getCaminhoArquivoPdf())));
    }
    String primeiroNome = arquivos.isEmpty() ? null : arquivos.getFirst().nomeArquivo();
    return ItemPagamentoParcelaResponse.from(parcela, primeiroNome, arquivos);
  }

  private static String extractFileName(String path) {
    if (path == null || path.isBlank()) {
      return null;
    }
    int slashIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    return slashIndex >= 0 ? path.substring(slashIndex + 1) : path;
  }
}
