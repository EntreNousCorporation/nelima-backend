package com.ypyit.neoelima.domain.user.mapper;

import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.user.dto.AddressDto;
import com.ypyit.neoelima.domain.user.entity.AddressEntity;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AddressMapper {

    AddressDto toDto(AddressEntity address);

    List<AddressDto> toDtos(List<EstablishmentEntity> addresses);
}
