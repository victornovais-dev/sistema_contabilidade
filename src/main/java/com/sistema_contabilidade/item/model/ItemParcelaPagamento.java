package com.sistema_contabilidade.item.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "itens_parcelas_pagamento")
@Getter
@Setter
@NoArgsConstructor
public class ItemParcelaPagamento {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  private UUID id;

  @Column(nullable = false)
  private Integer numero;

  @Column(name = "valor_parcela", nullable = false, precision = 15, scale = 2)
  private BigDecimal valorParcela;

  @Column(nullable = false)
  private boolean paga;

  @Enumerated(EnumType.STRING)
  @Column(name = "conta_origem_pagamento", length = 20)
  private ContaOrigemPagamentoItem contaOrigemPagamento;

  @Column(name = "caminho_arquivo_pdf", length = 500)
  private String caminhoArquivoPdf;

  @OneToMany(mappedBy = "parcelaPagamento", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ItemParcelaPagamentoArquivo> arquivosComprovantes = new ArrayList<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "item_id", nullable = false)
  private Item item;
}
