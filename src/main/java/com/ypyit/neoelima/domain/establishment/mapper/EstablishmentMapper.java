package com.ypyit.neoelima.domain.establishment.mapper;

import com.ypyit.neoelima.domain.establishment.dto.EstablishmentDto;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentLiteDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentCreationForm;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentRootCreationForm;
import com.ypyit.neoelima.domain.establishment.form.EstablishmentUpdateForm;
import com.ypyit.neoelima.domain.user.mapper.ContactMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", uses = ContactMapper.class, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EstablishmentMapper {

    @Mapping(target = "coverImage", ignore = true)
    EstablishmentEntity toEntity(EstablishmentRootCreationForm creationForm);

    @Mapping(target = "coverImage", ignore = true)
    EstablishmentEntity toEntity(EstablishmentCreationForm creationForm);

    // Le formulaire porte `Boolean isPrimary` (propriété « isPrimary »), l'entité `boolean isPrimary`
    // dont Lombok fait `setPrimary` (propriété « primary ») : sans cette correspondance explicite, les
    // deux noms ne se rejoignent pas et `toUpdate` n'écrivait jamais le drapeau. Une école dont il est
    // mal posé devient invisible partout — `findAll` filtre sur `isPrimary = true` — et l'API de
    // correction répondait 200 sans rien changer. Le null reste ignoré (stratégie ci-dessus), donc une
    // mise à jour partielle ne rétrograde pas l'établissement.
    @Mapping(target = "coverImage", ignore = true)
    @Mapping(target = "primary", source = "isPrimary")
    void toUpdate(EstablishmentUpdateForm updateForm, @MappingTarget EstablishmentEntity rib);

    @Mapping(target = "logo", source = "coverImage.link")
    EstablishmentDto toDto(EstablishmentEntity rib);

    @Mapping(target = "logo", source = "coverImage.link")
    EstablishmentLiteDto toLiteDto(EstablishmentEntity rib);

    List<EstablishmentDto> toDtos(List<EstablishmentEntity> establishments);

    Set<EstablishmentLiteDto> toLiteDtos(List<EstablishmentEntity> establishments);
}
