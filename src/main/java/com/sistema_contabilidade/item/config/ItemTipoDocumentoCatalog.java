package com.sistema_contabilidade.item.config;

import com.sistema_contabilidade.item.model.TipoItem;
import java.util.List;

public final class ItemTipoDocumentoCatalog {

  public static final String ITEM_TIPOS_DOCUMENTO_CACHE = "itemTiposDocumento";

  private static final List<ItemTipoDocumentoSeed> DEFAULT_REVENUE_DOCUMENT_TYPES =
      List.of(
          new ItemTipoDocumentoSeed("Pix", 10),
          new ItemTipoDocumentoSeed("Transferência", 20),
          new ItemTipoDocumentoSeed("Cheque", 30),
          new ItemTipoDocumentoSeed("Dinheiro", 40));

  private static final List<ItemTipoDocumentoSeed> DEFAULT_EXPENSE_DOCUMENT_TYPES =
      List.of(
          new ItemTipoDocumentoSeed("Nota fiscal", 10),
          new ItemTipoDocumentoSeed("Fatura", 20),
          new ItemTipoDocumentoSeed("Boleto", 30),
          new ItemTipoDocumentoSeed("Outros", 40));

  private ItemTipoDocumentoCatalog() {}

  public static List<ItemTipoDocumentoSeed> defaultDocumentTypes() {
    return DEFAULT_EXPENSE_DOCUMENT_TYPES;
  }

  public static List<ItemTipoDocumentoSeed> defaultDocumentTypesByTipo(TipoItem tipo) {
    return tipo == TipoItem.RECEITA
        ? DEFAULT_REVENUE_DOCUMENT_TYPES
        : DEFAULT_EXPENSE_DOCUMENT_TYPES;
  }

  public record ItemTipoDocumentoSeed(String nome, Integer ordem) {}
}
