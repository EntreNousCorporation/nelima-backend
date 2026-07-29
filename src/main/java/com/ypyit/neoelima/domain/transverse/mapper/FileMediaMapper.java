package com.ypyit.neoelima.domain.transverse.mapper;

import com.ypyit.neoelima.domain.transverse.entity.FileMediaEntity;
import com.ypyit.neoelima.domain.transverse.form.FileMediaCreateForm;
import com.ypyit.neoelima.domain.transverse.form.FileMediaUpdateForm;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FileMediaMapper {

    FileMediaEntity toEntity(FileMediaCreateForm form);

    FileMediaEntity toEntity(FileMediaUpdateForm form);
}
