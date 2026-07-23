package com.sistema_contabilidade.item.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "itens_parcelas_pagamento_arquivos")
@Getter
@Setter
@NoArgsConstructor
public class ItemParcelaPagamentoArquivo {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  private UUID id;

  @Column(name = "caminho_arquivo_pdf", nullable = false, length = 500)
  private String caminhoArquivoPdf;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parcela_pagamento_id", nullable = false)
  private ItemParcelaPagamento parcelaPagamento;
}
