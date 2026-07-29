package com.ypyit.neoelima.domain.storage.mapper;

import com.ypyit.neoelima.domain.storage.dto.StorageDto;
import com.ypyit.neoelima.domain.storage.form.StorageCreationForm;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface StorageMapper {

    StorageDto toDto(StorageCreationForm creationForm);
}
