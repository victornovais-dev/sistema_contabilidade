package com.sistema_contabilidade.common.mapper;

import com.sistema_contabilidade.usuario.dto.UsuarioDto;
import com.sistema_contabilidade.usuario.model.Usuario;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

  UsuarioDto toDto(Usuario usuario);

  @Mapping(target = "roles", ignore = true)
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "cognitoSub", ignore = true)
  @Mapping(target = "cognitoUsername", ignore = true)
  @Mapping(target = "cognitoGroupsHash", ignore = true)
  @Mapping(target = "cognitoSyncedAt", ignore = true)
  Usuario toEntity(UsuarioDto usuarioDto);
}
