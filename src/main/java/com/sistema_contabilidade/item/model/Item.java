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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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
@Table(name = "itens")
@Getter
@Setter
@NoArgsConstructor
public class Item {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  private UUID id;

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

  @Column(name = "razao_social", length = 200)
  private String razaoSocialNome;

  @Column(name = "cnpj_cpf", length = 20)
  private String cnpjCpf;

  @Column(length = 500)
  private String observacao;

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
