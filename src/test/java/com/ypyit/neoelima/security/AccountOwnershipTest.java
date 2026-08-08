package com.ypyit.neoelima.security;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.user.entity.AdminUserEntity;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.form.UserUpdateForm;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import com.ypyit.neoelima.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Personne ne touche au compte d'un autre.
 *
 * <p>Les deux routes qui portent un {@code {id}} de compte — la mise à jour du profil et la lecture
 * des enfants rattachés — n'étaient gardées que par « être authentifié ». Un parent connaissant
 * l'UUID d'un autre pouvait réécrire son contact principal et le verrouiller dehors (reproduit :
 * PUT en tant que parent → 200, la victime ne se connecte plus), ou lire ses enfants — matricule et
 * date de naissance, soit deux des trois preuves de rattachement. Un compte école, qui détient
 * {@code student:read}, récolte en masse les UUID de ses parents : le vecteur est réel.
 */
@Transactional
class AccountOwnershipTest extends AbstractIntegrationTest {

    @Autowired
    private CurrentUserProvider currentUserProvider;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private StudentRepository studentRepository;

    private UserEntity awa;
    private UserEntity didier;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.awa = this.aParent("Awa", "Traoré");
        this.didier = this.aParent("Didier", "Kouassi");
    }

    /* ---------- la garde, prise isolément ---------- */

    @Test
    @DisplayName("on agit sur son propre compte")
    void ownAccountIsAllowed() {
        this.authenticateAs(this.awa);

        assertThatCode(() -> this.currentUserProvider.assertCanManageUserAccount(this.awa.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("on n'agit pas sur le compte d'un autre")
    void anotherAccountIsRefused() {
        this.authenticateAs(this.awa);

        assertThatThrownBy(() -> this.currentUserProvider.assertCanManageUserAccount(this.didier.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("l'équipe YPYit passe partout")
    void platformAdminPasses() {
        this.authenticateAs(this.anAdmin());

        assertThatCode(() -> this.currentUserProvider.assertCanManageUserAccount(this.didier.getId()))
                .doesNotThrowAnyException();
    }

    /* ---------- la garde, câblée dans les vraies routes ---------- */

    @Test
    @DisplayName("A1 — un parent ne met pas à jour le profil d'un autre")
    void cannotUpdateAnotherProfile() {
        this.authenticateAs(this.awa);

        // Le défaut reproduit : cette requête rendait 200 et réécrivait le contact de Didier.
        assertThatThrownBy(() -> this.userService.update(this.didier.getId(),
                UserUpdateForm.builder().firstName("Pirate").build()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("A3 — un parent ne lit pas les enfants d'un autre")
    void cannotReadAnotherChildren() {
        this.aChildOf(this.didier);
        this.authenticateAs(this.awa);

        assertThatThrownBy(() -> this.userService.findStudents(this.didier.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("un parent lit bien ses propres enfants")
    void readsOwnChildren() {
        this.aChildOf(this.awa);
        this.authenticateAs(this.awa);

        assertThatCode(() -> this.userService.findStudents(this.awa.getId()))
                .doesNotThrowAnyException();
    }

    /* ---------- fabriques ---------- */

    private UserEntity aParent(String firstName, String lastName) {
        String email = "parent-" + UUID.randomUUID() + "@gmail.com";
        return this.userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName(firstName).lastName(lastName)
                .contacts(primaryEmail(email)).build());
    }

    private UserEntity anAdmin() {
        String email = "admin-" + UUID.randomUUID() + "@ypyit.com";
        return this.userRepository.saveAndFlush(AdminUserEntity.builder()
                .firstName("Nyky").lastName("Kof")
                .contacts(primaryEmail(email)).build());
    }

    private void aChildOf(UserEntity parent) {
        EstablishmentEntity school = this.establishmentRepository.saveAndFlush(
                EstablishmentEntity.builder()
                        .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
        StudentEntity child = this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Marie").lastName("Kouassi")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2013, 9, 12))
                .establishment(school).build());
        Set<UserEntity> parents = new HashSet<>(child.getParentUsers());
        parents.add(parent);
        child.setParentUsers(parents);
        this.studentRepository.saveAndFlush(child);
    }

    private static Set<ContactEntity> primaryEmail(String email) {
        return new HashSet<>(Set.of(ContactEntity.builder()
                .type(ContactType.EMAIL).value(email).isPrimary(true).build()));
    }

    private void authenticateAs(UserEntity user) {
        String email = user.getContacts().stream()
                .filter(ContactEntity::isPrimary)
                .map(ContactEntity::getValue)
                .findFirst().orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
