package com.ypyit.neoelima.domain.establishment.mapper;

import com.ypyit.neoelima.domain.establishment.dto.EstablishmentDto;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentLiteDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentCreationForm;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentRootCreationForm;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentUpdateForm;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EstablishmentMapper {

    @Mapping(target = "coverImage", ignore = true)
    EstablishmentEntity toEntity(EstablishmentRootCreationForm creationForm);

    @Mapping(target = "coverImage", ignore = true)
    EstablishmentEntity toEntity(EstablishmentCreationForm creationForm);

    @Mapping(target = "coverImage", ignore = true)
    void toUpdate(EstablishmentUpdateForm updateForm, @MappingTarget EstablishmentEntity rib);

    @Mapping(target = "logo", source = "coverImage.link")
    EstablishmentDto toDto(EstablishmentEntity rib);

    @Mapping(target = "logo", source = "coverImage.link")
    EstablishmentLiteDto toLiteDto(EstablishmentEntity rib);

    List<EstablishmentDto> toDtos(List<EstablishmentEntity> establishments);

    Set<EstablishmentLiteDto> toLiteDtos(List<EstablishmentEntity> establishments);
}
