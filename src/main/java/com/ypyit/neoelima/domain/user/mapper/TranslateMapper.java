package com.ypyit.neoelima.domain.user.mapper;


import com.ypyit.neoelima.domain.user.dto.TranslateDto;
import com.ypyit.neoelima.domain.user.entity.TranslateEntity;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.Set;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TranslateMapper {

    TranslateEntity toEntity(TranslateDto dto);

    Set<TranslateDto> toDtos(Set<TranslateEntity> entities);
}
