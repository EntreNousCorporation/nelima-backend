package com.ypyit.neoelima.domain.establishment.mapper;

import com.ypyit.neoelima.domain.establishment.dto.FeeScheduleDto;
import com.ypyit.neoelima.domain.establishment.entity.FeeScheduleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FeeScheduleMapper {

    FeeScheduleDto toDto(FeeScheduleEntity schedule);

    List<FeeScheduleDto> toDtos(List<FeeScheduleEntity> schedules);
}
