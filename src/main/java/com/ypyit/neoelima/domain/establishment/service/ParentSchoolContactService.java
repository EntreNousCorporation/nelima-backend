package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentContactCardDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.user.dto.ContactDto;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.mapper.ContactMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * De quoi joindre l'école de son enfant.
 *
 * <p>Les coordonnées d'un établissement existaient déjà mais restaient hors de portée d'un parent :
 * la liste des écoles ne les sert pas, et le détail complet exige d'administrer l'établissement.
 * Une famille devait donc chercher ailleurs le numéro de l'école de son enfant.
 *
 * <p><strong>La portée vient des enfants rattachés, jamais du paramètre.</strong> Ouvrir cette
 * fiche à tout compte authentifié ferait de l'application un annuaire des établissements du pays,
 * numéros de direction compris.
 *
 * <p>Volontairement plus étroit que la fiche complète : ce qui sert à appeler, écrire ou trouver
 * l'école, et rien d'autre.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParentSchoolContactService {

    private final StudentRepository studentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ContactMapper contactMapper;

    public EstablishmentContactCardDto contactCardOf(UUID establishmentId) {
        UUID parentId = this.currentUserProvider.currentUser().getId();

        EstablishmentEntity school = this.studentRepository.findByParentUsers_Id(parentId).stream()
                .map(StudentEntity::getEstablishment)
                .filter(Objects::nonNull)
                .filter(candidate -> Objects.equals(candidate.getId(), establishmentId))
                .findFirst()
                // Un refus et non un 404 : distinguer « école inconnue » de « école qui n'est pas
                // la vôtre » permettrait de sonder l'existence d'un établissement.
                .orElseThrow(() -> new AccessDeniedException(
                        "This establishment is not attached to any of your children"));

        return EstablishmentContactCardDto.builder()
                .id(school.getId())
                .name(school.getName())
                .webSite(school.getWebSite())
                .city(school.getCity())
                .address(Objects.isNull(school.getAddress()) ? null : school.getAddress().getName())
                .contacts(this.contactsOf(school))
                .build();
    }

    /**
     * Les coordonnées, le contact principal en tête.
     *
     * <p>C'est celui que l'école a désigné comme joignable ; le reléguer plus bas enverrait les
     * familles vers un numéro secondaire.
     */
    private List<ContactDto> contactsOf(EstablishmentEntity school) {
        List<ContactEntity> contacts = school.getContacts().stream()
                .sorted(Comparator.comparing(ContactEntity::isPrimary).reversed())
                .toList();
        return this.contactMapper.toDtos(contacts);
    }
}
