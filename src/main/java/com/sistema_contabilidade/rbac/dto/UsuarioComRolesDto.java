package com.sistema_contabilidade.rbac.dto;

import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioComRolesDto {

  private UUID id;
  private String nome;
  private String email;
  private Set<RoleResumoDto> roles;
}
