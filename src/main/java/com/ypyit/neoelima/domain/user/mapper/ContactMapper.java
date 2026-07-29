package com.ypyit.neoelima.domain.user.mapper;

import com.ypyit.neoelima.domain.user.dto.ContactDto;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.form.ContactCreationForm;
import com.ypyit.neoelima.domain.user.form.ContactUpdateForm;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ContactMapper {

    ContactDto toDto(ContactEntity contact);

    List<ContactDto> toDtos(List<ContactEntity> contacts);

    ContactEntity toEntity(ContactCreationForm form);

    ContactEntity toEntity(ContactDto dto);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "modifiedBy", ignore = true)
    void toUpdate(ContactUpdateForm updateForm, @MappingTarget ContactEntity entity);

    ContactCreationForm toCreate(ContactUpdateForm form);
}
