package com.sistema_contabilidade.common.mapper;

import com.sistema_contabilidade.usuario.dto.UsuarioDto;
import com.sistema_contabilidade.usuario.model.Usuario;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

  UsuarioDto toDto(Usuario usuario);

  Usuario toEntity(UsuarioDto usuarioDto);
}
