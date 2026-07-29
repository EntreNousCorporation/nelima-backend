package com.ypyit.neoelima.domain.establishment.mapper;

import com.ypyit.neoelima.domain.establishment.dto.LevelOfStudyDto;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LevelOfStudyMapper {

    LevelOfStudyDto toDto(LevelOfStudyEntity levelOfStudy);

    List<LevelOfStudyDto> toDtos(List<LevelOfStudyEntity> levelOfStudies);
}
