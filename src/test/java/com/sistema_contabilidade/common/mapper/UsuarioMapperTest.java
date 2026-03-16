package com.sistema_contabilidade.common.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sistema_contabilidade.usuario.dto.UsuarioDto;
import com.sistema_contabilidade.usuario.model.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

@DisplayName("UsuarioMapper unit tests")
class UsuarioMapperTest {

  private final UsuarioMapper mapper = Mappers.getMapper(UsuarioMapper.class);

  @Test
  @DisplayName("Deve mapear Usuario para UsuarioDto")
  void deveMapearUsuarioParaUsuarioDto() {
    Usuario usuario = new Usuario();
    usuario.setNome("Ana");
    usuario.setEmail("ana@email.com");
    usuario.setSenha("hash");

    UsuarioDto dto = mapper.toDto(usuario);

    assertEquals("Ana", dto.getNome());
    assertEquals("ana@email.com", dto.getEmail());
    assertEquals("hash", dto.getSenha());
  }

  @Test
  @DisplayName("Deve mapear UsuarioDto para Usuario")
  void deveMapearUsuarioDtoParaUsuario() {
    UsuarioDto dto = new UsuarioDto();
    dto.setNome("Bia");
    dto.setEmail("bia@email.com");
    dto.setSenha("123456");

    Usuario usuario = mapper.toEntity(dto);

    assertEquals("Bia", usuario.getNome());
    assertEquals("bia@email.com", usuario.getEmail());
    assertEquals("123456", usuario.getSenha());
  }
}
