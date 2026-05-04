package com.sistema_contabilidade.item.model;

import com.sistema_contabilidade.usuario.model.Usuario;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "itens",
    indexes = {
      @Index(name = "idx_itens_horario_id", columnList = "horario_criacao, id"),
      @Index(name = "idx_itens_role_horario_id", columnList = "role_nome, horario_criacao, id")
    })
@Getter
@Setter
@NoArgsConstructor
public class Item {

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

  @Column(name = "caminho_arquivo_pdf", length = 500)
  private String caminhoArquivoPdf;

  @Column(length = 120)
  private String descricao;

  @Column(name = "tipo_documento", length = 80)
  private String tipoDocumento;

  @Column(name = "numero_documento", length = 50)
  private String numeroDocumento;

  @Column(name = "razao_social", length = 150)
  private String razaoSocialNome;

  @Column(name = "cnpj_cpf", length = 20)
  private String cnpjCpf;

  @Column(length = 500)
  private String observacao;

  @Column(nullable = false)
  private boolean verificado;

  @Column(name = "role_nome", length = 100)
  private String roleNome;

  @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ItemArquivo> arquivos = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TipoItem tipo;

  @ManyToOne
  @JoinColumn(name = "criado_por_id")
  private Usuario criadoPor;
}
