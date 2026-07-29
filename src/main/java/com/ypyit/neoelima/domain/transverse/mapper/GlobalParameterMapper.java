package com.ypyit.neoelima.domain.transverse.mapper;

import com.ypyit.neoelima.domain.transverse.dto.GlobalParameterDto;
import com.ypyit.neoelima.domain.transverse.entity.GlobalParameterEntity;
import com.ypyit.neoelima.domain.transverse.form.GlobalParameterUpdateForm;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GlobalParameterMapper {

    GlobalParameterDto toDto(GlobalParameterEntity entity);

    void toUpdate(GlobalParameterUpdateForm updateForm, @MappingTarget GlobalParameterEntity entity);
}
