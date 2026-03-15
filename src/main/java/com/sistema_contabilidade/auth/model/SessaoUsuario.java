package com.sistema_contabilidade.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "sessoes_usuario",
    indexes = {
      @Index(name = "idx_sessao_usuario_id", columnList = "usuario_id"),
      @Index(name = "idx_sessao_expira_em", columnList = "expira_em")
    })
@Getter
@Setter
@NoArgsConstructor
public class SessaoUsuario {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  private UUID id;

  @Column(name = "usuario_id", nullable = false)
  private UUID usuarioId;

  @Column(name = "criada_em", nullable = false)
  private LocalDateTime criadaEm;

  @Column(name = "expira_em", nullable = false)
  private LocalDateTime expiraEm;

  @Column(nullable = false)
  private boolean revogada;
}
