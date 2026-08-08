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

/**
 * <p><strong>Le drapeau « contact principal » se recopie à la main.</strong> Le champ s'appelle
 * {@code isPrimary} ; Lombok en tire {@code isPrimary()} / {@code setPrimary()}, que MapStruct lit
 * comme la propriété « primary », tandis que le constructeur fluide expose « isPrimary ». Les deux
 * noms ne se rejoignent pas, et {@link ReportingPolicy#IGNORE} laissait la correspondance manquante
 * passer sans un mot : le drapeau se perdait à chaque conversion.
 *
 * <p>Ce n'était pas un défaut d'affichage. {@code ContactServiceImpl.createOrUpdate()} refait ses
 * entités depuis les DTO qu'il vient de produire, si bien qu'une modification de profil rendait
 * <em>tous</em> les contacts secondaires — et la connexion cherche justement le compte par son
 * contact principal ({@code UserRepository.findByPrimaryContact}). Un parent qui corrigeait son nom
 * ne pouvait plus se connecter.
 *
 * <p><strong>C'est le seul endroit où un contact se convertit.</strong> {@code UserMapper},
 * {@code StudentMapper} et {@code EstablishmentMapper} produisaient chacun leur propre conversion,
 * en ligne et identique — donc amputée du même drapeau, quatre fois. Ils déclarent désormais
 * {@code uses = ContactMapper.class} : corriger ici corrige partout, et une cinquième copie ne peut
 * plus naître par simple inadvertance.
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ContactMapper {

    @Mapping(target = "isPrimary", source = "primary")
    ContactDto toDto(ContactEntity contact);

    List<ContactDto> toDtos(List<ContactEntity> contacts);

    ContactEntity toEntity(ContactCreationForm form);

    @Mapping(target = "isPrimary", source = "primary")
    ContactEntity toEntity(ContactDto dto);

    @Mapping(target = "primary", source = "isPrimary")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "modifiedBy", ignore = true)
    void toUpdate(ContactUpdateForm updateForm, @MappingTarget ContactEntity entity);

    ContactCreationForm toCreate(ContactUpdateForm form);
}
