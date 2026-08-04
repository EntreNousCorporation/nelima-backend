package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.domain.establishment.dto.StaffDto;
import com.ypyit.neoelima.domain.establishment.entity.StaffEntity;
import com.ypyit.neoelima.domain.establishment.form.StaffAccessForm;
import com.ypyit.neoelima.domain.establishment.repository.StaffRepository;
import com.ypyit.neoelima.domain.user.dto.UserDto;
import com.ypyit.neoelima.domain.user.entity.RoleEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.enums.RoleType;
import com.ypyit.neoelima.domain.user.form.ContactCreationForm;
import com.ypyit.neoelima.domain.user.form.EstablishmentUserSignupForm;
import com.ypyit.neoelima.domain.user.repository.RoleRepository;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import com.ypyit.neoelima.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Ouverture d'un accès au portail pour un membre du personnel.
 *
 * <p>Le lien est facultatif dans les deux sens : une fiche peut vivre sans compte — la plupart des
 * enseignants n'ont pas accès au portail — et le compte survit à la fiche pour que les
 * encaissements qu'il a saisis gardent un auteur.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffAccessService {

    /**
     * Rôles qu'une école peut attribuer.
     *
     * <p>Ni {@code ADMIN} — l'équipe YPYit ne se recrute pas depuis un portail école — ni
     * {@code ESTABLISHMENT_ROOT}, qui cumule toutes les permissions : le proposer ici viderait de
     * son sens le découpage qu'on vient d'introduire.
     */
    private static final Set<RoleType> GRANTABLE =
            Set.of(RoleType.DIRECTEUR, RoleType.COMPTABLE, RoleType.SECRETARIAT);

    private final StaffService staffService;
    private final StaffRepository staffRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional
    public StaffDto grant(UUID staffId, StaffAccessForm form) {
        StaffEntity member = this.staffService.load(staffId);

        if (Objects.nonNull(member.getUser())) {
            throw new BadRequestException(String.format(
                    "%s dispose déjà d'un accès au portail.", member.getFirstName()));
        }
        if (!GRANTABLE.contains(form.getRole())) {
            throw new BadRequestException(String.format(
                    "Le rôle %s ne peut pas être attribué depuis le portail.", form.getRole()));
        }

        RoleEntity role = this.roleRepository.findByCode(form.getRole().name())
                .orElseThrow(() -> new NotFoundException(
                        String.format("Role with code %s not found", form.getRole())));

        ContactCreationForm contact = new ContactCreationForm();
        contact.setType(ContactType.EMAIL);
        contact.setValue(form.getUsername().trim());
        contact.setIsPrimary(true);

        EstablishmentUserSignupForm signup = new EstablishmentUserSignupForm();
        signup.setFirstName(member.getFirstName());
        signup.setLastName(member.getLastName());
        signup.setRoleId(role.getId());
        signup.setContacts(new HashSet<>(Set.of(contact)));

        // On délègue à la création de compte partenaire existante : c'est elle qui valide les
        // contacts et envoie le courriel de bienvenue portant le lien de définition du mot de
        // passe. Refaire ce chemin ici créerait un compte sans moyen d'y entrer.
        UserDto created;
        try {
            created = this.userService.createPartnerUser(member.getEstablishment().getId(), signup);
        } catch (Exception e) {
            throw new BadRequestException("L'accès n'a pas pu être ouvert : " + e.getMessage());
        }

        UserEntity user = this.userRepository.findById(created.getId())
                .orElseThrow(() -> new NotFoundException("Created user not found"));
        member.setUser(user);
        this.staffRepository.saveAndFlush(member);

        log.info("STAFF_ACCESS_GRANTED: staff {} role {}", staffId, form.getRole());
        return this.staffService.findById(staffId);
    }

    /**
     * Ferme l'accès sans supprimer le compte.
     *
     * <p>Les reçus et les encaissements saisis portent son nom : effacer le compte les rendrait
     * anonymes. On le désactive, ce qui suffit à interdire la connexion.
     */
    @Transactional
    public StaffDto revoke(UUID staffId) {
        StaffEntity member = this.staffService.load(staffId);
        UserEntity user = member.getUser();
        if (Objects.isNull(user)) {
            throw new BadRequestException("Ce membre n'a pas d'accès au portail.");
        }
        user.setEnabled(false);
        this.userRepository.saveAndFlush(user);
        log.info("STAFF_ACCESS_REVOKED: staff {}", staffId);
        return this.staffService.findById(staffId);
    }
}
