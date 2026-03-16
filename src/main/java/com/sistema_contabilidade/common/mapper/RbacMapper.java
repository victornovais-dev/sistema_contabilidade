package com.sistema_contabilidade.common.mapper;

import com.sistema_contabilidade.rbac.dto.PermissaoDto;
import com.sistema_contabilidade.rbac.dto.RoleDto;
import com.sistema_contabilidade.rbac.dto.RoleResumoDto;
import com.sistema_contabilidade.rbac.dto.UsuarioComRolesDto;
import com.sistema_contabilidade.rbac.model.Permissao;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.usuario.model.Usuario;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface RbacMapper {

  RoleDto toRoleDto(Role role);

  PermissaoDto toPermissaoDto(Permissao permissao);

  UsuarioComRolesDto toUsuarioComRolesDto(Usuario usuario);

  RoleResumoDto toRoleResumoDto(Role role);
}
