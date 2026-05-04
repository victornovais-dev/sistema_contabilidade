package com.sistema_contabilidade.item.dto;

import com.sistema_contabilidade.item.model.TipoItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class ItemListPageRequest {

  @Min(1)
  private int page = 1;

  @Min(1)
  @Max(100)
  private int pageSize = 10;

  private String role;
  private TipoItem tipo;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate dataInicio;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate dataFim;

  private String descricao;
  private String razao;
}
