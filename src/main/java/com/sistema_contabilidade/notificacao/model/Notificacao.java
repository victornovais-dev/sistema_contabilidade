package com.sistema_contabilidade.notificacao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "notificacoes")
@Getter
@Setter
@NoArgsConstructor
public class Notificacao {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  private UUID id;

  @Column(name = "item_id", nullable = false)
  private UUID itemId;

  @Column(name = "role_nome", nullable = false, length = 100)
  private String roleNome;

  @Column(length = 120)
  private String descricao;

  @Column(name = "razao_social_nome", length = 150)
  private String razaoSocialNome;

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal valor;

  @Column(name = "criado_em", nullable = false)
  private LocalDateTime criadoEm;

  @Column(nullable = false)
  private boolean limpa;
}
