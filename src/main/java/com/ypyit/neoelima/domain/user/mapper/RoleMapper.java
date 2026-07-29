package com.ypyit.neoelima.domain.user.mapper;

import com.ypyit.neoelima.domain.user.dto.RoleDto;
import com.ypyit.neoelima.domain.user.entity.RoleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RoleMapper {

    RoleDto toDto(RoleEntity role);

    List<RoleDto> toDtos(List<RoleEntity> roles);
}
