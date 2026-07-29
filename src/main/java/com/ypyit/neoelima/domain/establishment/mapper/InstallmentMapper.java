package com.ypyit.neoelima.domain.establishment.mapper;

import com.ypyit.neoelima.domain.establishment.dto.InstallmentDto;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.form.InstallmentCreationForm;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InstallmentMapper {

    InstallmentDto toDto(InstallmentEntity installment);

    InstallmentEntity toEntity(InstallmentCreationForm creationForm);

    List<InstallmentDto> toDtos(List<InstallmentEntity> installments);
}
