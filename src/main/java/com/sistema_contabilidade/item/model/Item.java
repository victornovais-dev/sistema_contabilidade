package com.sistema_contabilidade.item.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "itens")
@Getter
@Setter
@NoArgsConstructor
public class Item {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  private UUID id;

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal valor;

  @Column(nullable = false)
  private LocalDate data;

  @Column(name = "horario_criacao", nullable = false)
  private LocalDateTime horarioCriacao;

  @Column(name = "caminho_arquivo_pdf", length = 500)
  private String caminhoArquivoPdf;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TipoItem tipo;
}
