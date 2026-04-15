package com.sistema_contabilidade.item.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "item_tipos_documento",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_item_tipos_documento_nome",
          columnNames = {"nome"})
    })
@Getter
@Setter
@NoArgsConstructor
public class ItemTipoDocumento {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 80)
  private String nome;

  @Column(nullable = false)
  private Integer ordem;
}
