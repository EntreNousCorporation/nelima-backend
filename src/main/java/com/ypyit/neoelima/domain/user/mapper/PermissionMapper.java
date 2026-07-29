package com.ypyit.neoelima.domain.user.mapper;

import com.ypyit.neoelima.domain.user.dto.PermissionDto;
import com.ypyit.neoelima.domain.user.entity.PermissionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PermissionMapper {

    PermissionDto toDto(PermissionEntity permission);

    List<PermissionDto> toDtos(List<PermissionEntity> permissions);
}
