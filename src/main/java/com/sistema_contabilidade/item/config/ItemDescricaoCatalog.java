package com.sistema_contabilidade.item.config;

import com.sistema_contabilidade.item.model.TipoItem;
import java.util.List;

public final class ItemDescricaoCatalog {

  public static final String ITEM_DESCRICOES_CACHE = "itemDescricoes";

  private static final List<ItemDescricaoSeed> DEFAULT_DESCRIPTIONS =
      List.of(
          new ItemDescricaoSeed(TipoItem.RECEITA, "CONTA FEFC", 10),
          new ItemDescricaoSeed(TipoItem.RECEITA, "CONTA FP", 20),
          new ItemDescricaoSeed(TipoItem.RECEITA, "CONTA DC", 30),
          new ItemDescricaoSeed(TipoItem.RECEITA, "ESTIMÁVEL", 40),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Publicidade por materiais impressos", 10),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Publicidade na internet", 20),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Publicidade por carro de som", 30),
          new ItemDescricaoSeed(
              TipoItem.DESPESA, "Produção de programas de rádio, TV ou vídeo", 40),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Impulsionamento de conteúdo", 50),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Serviços prestados por terceiros", 60),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Serviços advocatícios", 70),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Serviços contábeis", 80),
          new ItemDescricaoSeed(
              TipoItem.DESPESA, "Atividades de militância e mobilização de rua", 90),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Remuneração de pessoal", 100),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Aluguel de imóveis", 110),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Aluguel de veículos", 120),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Combustíveis e lubrificantes", 130),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Energia elétrica", 140),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Água", 150),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Internet", 160),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Telefone", 170),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Material de expediente", 180),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Material de campanha (não publicitário)", 190),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Alimentação", 200),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Transporte ou deslocamento", 210),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Hospedagem", 220),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Organização de eventos", 230),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Produção de jingles, vinhetas e slogans", 240),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Produção de material gráfico", 250),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Criação e inclusão de páginas na internet", 260),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Manutenção de sites", 270),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Softwares e ferramentas digitais", 280),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Taxas bancárias", 290),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Encargos financeiros", 300),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Multas eleitorais", 310),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Doações a outros candidatos/partidos", 320),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Baixa de estimáveis em dinheiro", 330),
          new ItemDescricaoSeed(TipoItem.DESPESA, "Outras despesas", 9999));

  private ItemDescricaoCatalog() {}

  public static List<ItemDescricaoSeed> defaultDescriptions() {
    return DEFAULT_DESCRIPTIONS;
  }

  public record ItemDescricaoSeed(TipoItem tipo, String nome, Integer ordem) {}
}
