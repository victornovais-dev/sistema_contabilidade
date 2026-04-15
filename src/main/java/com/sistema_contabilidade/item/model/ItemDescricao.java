package com.sistema_contabilidade.item.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "item_descricoes",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_item_descricoes_tipo_nome",
          columnNames = {"tipo", "nome"})
    })
@Getter
@Setter
@NoArgsConstructor
public class ItemDescricao {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TipoItem tipo;

  @Column(nullable = false, length = 160)
  private String nome;

  @Column(nullable = false)
  private Integer ordem;
}
