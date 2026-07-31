package com.sistema_contabilidade.notificacao.model;

import com.sistema_contabilidade.database.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "notificacoes",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_notificacoes_item_id", columnNames = "item_id"))
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
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

  @Convert(converter = EncryptedStringConverter.class)
  @Column(length = 512)
  private String descricao;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "razao_social_nome", length = 512)
  private String razaoSocialNome;

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal valor;

  @Column(name = "criado_em", nullable = false)
  private LocalDateTime criadoEm;

  @Column(nullable = false)
  private boolean limpa;
}
