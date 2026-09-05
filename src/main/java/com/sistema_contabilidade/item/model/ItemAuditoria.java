package com.sistema_contabilidade.item.model;

import com.sistema_contabilidade.database.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "item_auditorias",
    indexes = {
      @Index(name = "idx_item_auditorias_item_ocorrido", columnList = "item_id, ocorrido_em, id"),
      @Index(name = "idx_item_auditorias_ocorrido", columnList = "ocorrido_em")
    })
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemAuditoria {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  private UUID id;

  @Column(name = "item_id", nullable = false, updatable = false)
  private UUID itemId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 32)
  private ItemAuditoriaAcao acao;

  @Column(name = "ator_id", nullable = false, updatable = false)
  private UUID atorId;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "ator_nome", nullable = false, updatable = false, length = 512)
  private String atorNome;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "ator_email", nullable = false, updatable = false, length = 512)
  private String atorEmail;

  @Column(name = "ocorrido_em", nullable = false, updatable = false)
  private Instant ocorridoEm;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "ip_origem", nullable = false, updatable = false, length = 256)
  private String ipOrigem;

  @Column(name = "user_agent", updatable = false, length = 1024)
  private String userAgent;

  @Column(name = "trace_id", nullable = false, updatable = false, length = 32)
  private String traceId;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(nullable = false, updatable = false, columnDefinition = "MEDIUMTEXT")
  private String alteracoes;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "snapshot_item", updatable = false, columnDefinition = "MEDIUMTEXT")
  private String snapshotItem;

  public record Ator(UUID id, String nome, String email) {

    public Ator {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(nome, "nome");
      Objects.requireNonNull(email, "email");
    }
  }

  public record ContextoRequisicao(
      Instant ocorridoEm, String ipOrigem, String userAgent, String traceId) {

    public ContextoRequisicao {
      Objects.requireNonNull(ocorridoEm, "ocorridoEm");
      Objects.requireNonNull(ipOrigem, "ipOrigem");
      Objects.requireNonNull(traceId, "traceId");
    }
  }

  public record ConteudoEvento(String alteracoes, String snapshotItem) {

    public ConteudoEvento {
      Objects.requireNonNull(alteracoes, "alteracoes");
    }
  }

  public ItemAuditoria(
      UUID itemId,
      ItemAuditoriaAcao acao,
      Ator ator,
      ContextoRequisicao contextoRequisicao,
      ConteudoEvento conteudoEvento) {
    this.itemId = Objects.requireNonNull(itemId, "itemId");
    this.acao = Objects.requireNonNull(acao, "acao");
    Ator atorValidado = Objects.requireNonNull(ator, "ator");
    ContextoRequisicao contextoValidado =
        Objects.requireNonNull(contextoRequisicao, "contextoRequisicao");
    ConteudoEvento conteudoValidado = Objects.requireNonNull(conteudoEvento, "conteudoEvento");
    this.atorId = atorValidado.id();
    this.atorNome = atorValidado.nome();
    this.atorEmail = atorValidado.email();
    this.ocorridoEm = contextoValidado.ocorridoEm();
    this.ipOrigem = contextoValidado.ipOrigem();
    this.userAgent = contextoValidado.userAgent();
    this.traceId = contextoValidado.traceId();
    this.alteracoes = conteudoValidado.alteracoes();
    this.snapshotItem = conteudoValidado.snapshotItem();
  }
}
