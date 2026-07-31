package com.sistema_contabilidade.privacidade.model;

import com.sistema_contabilidade.database.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "solicitacoes_privacidade_eventos")
@Getter
@Setter
@NoArgsConstructor
public class SolicitacaoPrivacidadeEvento {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "solicitacao_id", nullable = false)
  private SolicitacaoPrivacidade solicitacao;

  @Column(nullable = false, length = 120)
  private String titulo;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(nullable = false, length = 2048)
  private String descricao;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(nullable = false, length = 512)
  private String ator;

  @Column(name = "ocorrido_em", nullable = false)
  private LocalDateTime ocorridoEm;
}
