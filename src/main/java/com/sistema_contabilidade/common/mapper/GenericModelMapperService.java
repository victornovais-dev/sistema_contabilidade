package com.sistema_contabilidade.common.mapper;

import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class GenericModelMapperService<E, D> {

    private final ModelMapper modelMapper;

    public D convertToDto(E entity, Class<D> dtoClass) {
        return modelMapper.map(entity, dtoClass);
    }

    public E convertToEntity(D dto, Class<E> entityClass) {
        return modelMapper.map(dto, entityClass);
    }

    public void mapToEntity(D dto, E entity) {
        modelMapper.map(dto, entity);
    }
}
