package com.sistema_contabilidade.item.model;

import com.sistema_contabilidade.database.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "itens_arquivos")
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
@Getter
@Setter
@NoArgsConstructor
public class ItemArquivo {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  private UUID id;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "caminho_arquivo_pdf", nullable = false, length = 1024)
  private String caminhoArquivoPdf;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "item_id", nullable = false)
  private Item item;
}
