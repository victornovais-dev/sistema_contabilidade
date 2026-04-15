package com.sistema_contabilidade.item.config;

import java.util.List;

public final class ItemTipoDocumentoCatalog {

  public static final String ITEM_TIPOS_DOCUMENTO_CACHE = "itemTiposDocumento";

  private static final List<ItemTipoDocumentoSeed> DEFAULT_DOCUMENT_TYPES =
      List.of(
          new ItemTipoDocumentoSeed("Nota fiscal", 10),
          new ItemTipoDocumentoSeed("Fatura", 20),
          new ItemTipoDocumentoSeed("Boleto", 30),
          new ItemTipoDocumentoSeed("Outros", 40));

  private ItemTipoDocumentoCatalog() {}

  public static List<ItemTipoDocumentoSeed> defaultDocumentTypes() {
    return DEFAULT_DOCUMENT_TYPES;
  }

  public record ItemTipoDocumentoSeed(String nome, Integer ordem) {}
}
