package com.sistema_contabilidade.privacidade.model;

import com.sistema_contabilidade.database.crypto.BlindIndexAware;
import com.sistema_contabilidade.database.crypto.BlindIndexEntityListener;
import com.sistema_contabilidade.database.crypto.EncryptedStringConverter;
import com.sistema_contabilidade.database.crypto.service.BlindIndexService;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "solicitacoes_privacidade",
    indexes = {
      @Index(name = "idx_sol_priv_protocolo", columnList = "protocolo", unique = true),
      @Index(name = "idx_sol_priv_email_bidx", columnList = "email_bidx"),
      @Index(name = "idx_sol_priv_status_prazo", columnList = "status,prazo")
    })
@EntityListeners(BlindIndexEntityListener.class)
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
@Getter
@Setter
@NoArgsConstructor
public class SolicitacaoPrivacidade implements BlindIndexAware {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  private UUID id;

  @Version private Long version;

  @Column(nullable = false, unique = true, length = 32)
  private String protocolo;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "nome_titular", nullable = false, length = 512)
  private String nomeTitular;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "email_titular", nullable = false, length = 512)
  private String emailTitular;

  @Column(name = "email_bidx", nullable = false, length = 64)
  private String emailBlindIndex;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(nullable = false, length = 512)
  private String organizacao;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private SolicitacaoPrivacidadeVinculo vinculo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private SolicitacaoPrivacidadeTipo tipo;

  @Column(nullable = false, length = 255)
  private String escopos;

  @Enumerated(EnumType.STRING)
  @Column(name = "canal_resposta", nullable = false, length = 20)
  private SolicitacaoPrivacidadeCanal canalResposta;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "referencia_titular", length = 512)
  private String referenciaTitular;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private SolicitacaoPrivacidadeStatus status;

  @Column(name = "recebida_em", nullable = false)
  private LocalDate recebidaEm;

  @Column(nullable = false)
  private LocalDate prazo;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(nullable = false, length = 512)
  private String responsavel;

  @Column(name = "identidade_verificada", nullable = false)
  private boolean identidadeVerificada;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(nullable = false, length = 4096)
  private String descricao;

  @Column(name = "retencao_legal", nullable = false)
  private boolean retencaoLegal;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "motivo_retencao", length = 2048)
  private String motivoRetencao;

  @Column(name = "versao_aviso", nullable = false, length = 20)
  private String versaoAviso;

  @Column(name = "criada_em", nullable = false)
  private LocalDateTime criadaEm;

  @Column(name = "atualizada_em", nullable = false)
  private LocalDateTime atualizadaEm;

  @OneToMany(mappedBy = "solicitacao", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("ocorridoEm DESC")
  private List<SolicitacaoPrivacidadeEvento> eventos = new ArrayList<>();

  @Override
  public void synchronizeBlindIndexes(BlindIndexService blindIndexService) {
    this.emailBlindIndex = blindIndexService.email(emailTitular);
  }

  public void adicionarEvento(SolicitacaoPrivacidadeEvento evento) {
    evento.setSolicitacao(this);
    eventos.add(evento);
  }
}
