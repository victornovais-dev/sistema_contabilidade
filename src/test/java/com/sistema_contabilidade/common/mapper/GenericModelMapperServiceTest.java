package com.sistema_contabilidade.common.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.usuario.dto.UsuarioDto;
import com.sistema_contabilidade.usuario.model.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("GenericModelMapperService unit tests")
class GenericModelMapperServiceTest {

  @Mock private ModelMapper modelMapper;

  @Test
  @DisplayName("Deve converter entidade para DTO")
  void deveConverterEntidadeParaDto() {
    GenericModelMapperService<Usuario, UsuarioDto> service =
        new GenericModelMapperService<>(modelMapper);
    Usuario entity = new Usuario();
    UsuarioDto dto = new UsuarioDto();
    dto.setNome("Ana");
    when(modelMapper.map(entity, UsuarioDto.class)).thenReturn(dto);

    UsuarioDto result = service.convertToDto(entity, UsuarioDto.class);

    assertEquals("Ana", result.getNome());
  }

  @Test
  @DisplayName("Deve converter DTO para entidade")
  void deveConverterDtoParaEntidade() {
    GenericModelMapperService<Usuario, UsuarioDto> service =
        new GenericModelMapperService<>(modelMapper);
    UsuarioDto dto = new UsuarioDto();
    dto.setEmail("ana@email.com");
    Usuario entity = new Usuario();
    entity.setEmail("ana@email.com");
    when(modelMapper.map(dto, Usuario.class)).thenReturn(entity);

    Usuario result = service.convertToEntity(dto, Usuario.class);

    assertEquals("ana@email.com", result.getEmail());
  }

  @Test
  @DisplayName("Deve mapear DTO para entidade existente")
  void deveMapearDtoParaEntidadeExistente() {
    GenericModelMapperService<Usuario, UsuarioDto> service =
        new GenericModelMapperService<>(modelMapper);
    UsuarioDto dto = new UsuarioDto();
    Usuario entity = new Usuario();

    service.mapToEntity(dto, entity);

    verify(modelMapper).map(dto, entity);
  }
}
