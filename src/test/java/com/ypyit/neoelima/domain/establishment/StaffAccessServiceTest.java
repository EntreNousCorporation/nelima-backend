package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.domain.establishment.dto.StaffDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.enums.StaffRole;
import com.ypyit.neoelima.domain.establishment.form.StaffAccessForm;
import com.ypyit.neoelima.domain.establishment.form.StaffForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.service.StaffAccessService;
import com.ypyit.neoelima.domain.establishment.service.StaffService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.PermissionEntity;
import com.ypyit.neoelima.domain.user.entity.RoleEntity;
import com.ypyit.neoelima.domain.user.entity.TranslateEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.enums.RoleType;
import com.ypyit.neoelima.domain.user.repository.PermissionRepository;
import com.ypyit.neoelima.domain.user.repository.RoleRepository;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ouverture d'un accès au portail depuis une fiche du personnel.
 *
 * <p>Le point sensible est la liste des rôles attribuables : une école qui pourrait s'octroyer le
 * rôle d'amorçage, ou celui de l'équipe YPYit, viderait de son sens tout le découpage des droits.
 */
@Transactional
class StaffAccessServiceTest extends AbstractIntegrationTest {

    @Autowired
    private StaffService staffService;
    @Autowired
    private StaffAccessService accessService;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;

    private EstablishmentEntity school;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.school = this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
        // Les rôles attribuables doivent exister : c'est le catalogue semé au démarrage en
        // production, absent de la base de test.
        this.role(RoleType.SECRETARIAT.name());
        this.role(RoleType.ESTABLISHMENT_ROOT.name());
        this.authenticate("staff:read", "staff:write", "user_access:write");
    }

    @Test
    @DisplayName("l'accès ouvert rattache un compte à la fiche")
    void grantsAccess() {
        StaffDto member = this.member("KOUAMÉ");

        StaffDto withAccess = this.accessService.grant(
                UUID.fromString(member.getId()), this.form("secretariat@ecole.ci", RoleType.SECRETARIAT));

        assertThat(withAccess.getUserId()).isNotNull();
        assertThat(withAccess.getUsername()).isEqualTo("secretariat@ecole.ci");
        assertThat(withAccess.getRoleCode()).isEqualTo(RoleType.SECRETARIAT.name());
        assertThat(withAccess.isAccessEnabled()).isTrue();
    }

    @Test
    @DisplayName("le rôle d'amorçage n'est pas attribuable depuis le portail")
    void refusesTheBootstrapRole() {
        StaffDto member = this.member("BROU");

        // ESTABLISHMENT_ROOT cumule toutes les permissions : le proposer ici permettrait à une
        // école de contourner d'un clic le découpage des droits.
        assertThatThrownBy(() -> this.accessService.grant(UUID.fromString(member.getId()),
                this.form("root@ecole.ci", RoleType.ESTABLISHMENT_ROOT)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("un membre déjà pourvu d'un accès n'en reçoit pas un second")
    void refusesASecondAccess() {
        StaffDto member = this.member("ZOKOU");
        this.accessService.grant(UUID.fromString(member.getId()),
                this.form("premier@ecole.ci", RoleType.SECRETARIAT));

        assertThatThrownBy(() -> this.accessService.grant(UUID.fromString(member.getId()),
                this.form("second@ecole.ci", RoleType.SECRETARIAT)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("fermer l'accès désactive le compte sans le supprimer")
    void revokesWithoutDeleting() {
        StaffDto member = this.member("DIABATÉ");
        StaffDto withAccess = this.accessService.grant(UUID.fromString(member.getId()),
                this.form("compta@ecole.ci", RoleType.SECRETARIAT));
        UUID userId = UUID.fromString(withAccess.getUserId());

        StaffDto revoked = this.accessService.revoke(UUID.fromString(member.getId()));

        // Le compte survit : les encaissements qu'il a saisis portent son nom.
        assertThat(revoked.isAccessEnabled()).isFalse();
        assertThat(this.userRepository.findById(userId)).isPresent();
        assertThat(this.userRepository.findById(userId).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("fermer un accès inexistant est refusé plutôt qu'ignoré")
    void refusesToRevokeWhatDoesNotExist() {
        StaffDto member = this.member("YEO");

        assertThatThrownBy(() -> this.accessService.revoke(UUID.fromString(member.getId())))
                .isInstanceOf(BadRequestException.class);
    }

    private StaffAccessForm form(String username, RoleType role) {
        StaffAccessForm form = new StaffAccessForm();
        form.setUsername(username);
        form.setRole(role);
        return form;
    }

    private StaffDto member(String lastName) {
        StaffForm form = new StaffForm();
        form.setFirstName("Awa");
        form.setLastName(lastName);
        form.setRole(StaffRole.ADMINISTRATION);
        return this.staffService.create(form);
    }

    private RoleEntity role(String code) {
        return this.roleRepository.findByCode(code)
                .orElseGet(() -> this.roleRepository.saveAndFlush(RoleEntity.builder()
                        .code(code)
                        .name(TranslateEntity.builder().fr(code).en(code).build())
                        .build()));
    }

    private void authenticate(String... permissions) {
        Set<PermissionEntity> granted = new HashSet<>();
        for (String code : permissions) {
            granted.add(this.permissionRepository.findByCode(code)
                    .orElseGet(() -> this.permissionRepository.saveAndFlush(PermissionEntity.builder()
                            .code(code)
                            .name(TranslateEntity.builder().fr(code).en(code).build())
                            .build())));
        }

        RoleEntity role = this.roleRepository.saveAndFlush(RoleEntity.builder()
                .code("ROLE-" + UUID.randomUUID())
                .name(TranslateEntity.builder().fr("Rôle de test").en("Test role").build())
                .permissions(granted)
                .build());

        String email = "user-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(this.school).role(role)
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
