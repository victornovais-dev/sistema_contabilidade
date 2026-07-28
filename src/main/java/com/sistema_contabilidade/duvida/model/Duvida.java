package com.sistema_contabilidade.duvida.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "duvidas_publicas")
@Getter
@Setter
@NoArgsConstructor
public class Duvida {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  private UUID id;

  @Column(nullable = false, length = 120)
  private String nome;

  @Column(nullable = false, length = 255)
  private String email;

  @Column(name = "duvida", nullable = false, length = 1200)
  private String mensagem;

  @Column(name = "recebida_em", nullable = false)
  private LocalDateTime recebidaEm;

  @Column(nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private DuvidaStatus status = DuvidaStatus.PENDENTE;
}
