package com.sistema_contabilidade.item.model;

import com.sistema_contabilidade.common.util.SearchTextNormalizer;
import com.sistema_contabilidade.database.crypto.BlindIndexAware;
import com.sistema_contabilidade.database.crypto.BlindIndexEntityListener;
import com.sistema_contabilidade.database.crypto.EncryptedStringConverter;
import com.sistema_contabilidade.database.crypto.service.BlindIndexService;
import com.sistema_contabilidade.usuario.model.Usuario;
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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
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
    name = "itens",
    indexes = {
      @Index(name = "idx_itens_horario_id", columnList = "horario_criacao, id"),
      @Index(name = "idx_itens_role_horario_id", columnList = "role_nome, horario_criacao, id")
    })
@EntityListeners(BlindIndexEntityListener.class)
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
@Getter
@Setter
@NoArgsConstructor
public class Item implements BlindIndexAware {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  private UUID id;

  @Version private Long version;

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal valor;

  @Column(nullable = false)
  private LocalDate data;

  @Column(name = "horario_criacao", nullable = false)
  private LocalDateTime horarioCriacao;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "caminho_arquivo_pdf", length = 1024)
  private String caminhoArquivoPdf;

  @Column(length = 120)
  private String descricao;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "tipo_documento", length = 256)
  private String tipoDocumento;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "numero_documento", length = 256)
  private String numeroDocumento;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "razao_social", length = 512)
  private String razaoSocialNome;

  @Column(name = "razao_social_busca", length = 200)
  private String razaoSocialBusca;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "cnpj_cpf", length = 256)
  private String cnpjCpf;

  @Column(name = "cnpj_cpf_bidx", length = 64)
  private String cnpjCpfBlindIndex;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(length = 1024)
  private String observacao;

  @Enumerated(EnumType.STRING)
  @Column(name = "forma_pagamento", length = 20)
  private FormaPagamentoItem formaPagamento;

  @Column(nullable = false)
  private boolean verificado;

  @Column(name = "role_nome", length = 100)
  private String roleNome;

  @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ItemArquivo> arquivos = new ArrayList<>();

  @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ItemParcelaPagamento> parcelasPagamento = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TipoItem tipo;

  @ManyToOne
  @JoinColumn(name = "criado_por_id")
  private Usuario criadoPor;

  @SuppressWarnings("PMD.UnusedPrivateMethod")
  @PrePersist
  @PreUpdate
  private void synchronizeDerivedFields() {
    synchronizeSearchFields();
  }

  public void synchronizeSearchFields() {
    this.razaoSocialBusca = SearchTextNormalizer.normalizeForSearch(razaoSocialNome);
  }

  @Override
  public void synchronizeBlindIndexes(BlindIndexService blindIndexService) {
    this.cnpjCpfBlindIndex = blindIndexService.document(cnpjCpf);
  }
}
