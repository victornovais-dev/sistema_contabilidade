package com.sistema_contabilidade.duvida.model;

import com.sistema_contabilidade.database.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "duvidas_publicas")
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
@Getter
@Setter
@NoArgsConstructor
public class Duvida {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  private UUID id;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(nullable = false, length = 512)
  private String nome;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(nullable = false, length = 512)
  private String email;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "duvida", nullable = false, length = 2048)
  private String mensagem;

  @Column(name = "recebida_em", nullable = false)
  private LocalDateTime recebidaEm;

  @Column(nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private DuvidaStatus status = DuvidaStatus.PENDENTE;
}
