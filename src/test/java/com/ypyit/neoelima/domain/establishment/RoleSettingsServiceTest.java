package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.RoleSettingsDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.service.RoleSettingsService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.RoleEntity;
import com.ypyit.neoelima.domain.user.entity.TranslateEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.RoleRepository;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Habilitations réglées par une école : ses comptes, pas ceux des autres. */
@Transactional
class RoleSettingsServiceTest extends AbstractIntegrationTest {

    @Autowired
    private RoleSettingsService roleSettingsService;
    @Autowired
    private CurrentUserProvider currentUserProvider;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;

    private EstablishmentEntity school;
    private EstablishmentEntity other;
    private String admin;
    private String accountant;
    private String otherAccountant;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.school = this.school();
        this.other = this.school();
        // Le catalogue n'est pas semé dans la base de test : les permissions utilisées sont créées ici.
        roleWith("student:write", "student:read", "settings:write", "settings:read");
        RoleEntity accountantRole = this.role("COMPTABLE", "fee:read", "accounting:read");
        this.role("DIRECTEUR", "fee:read", "user_access:write");
        this.role("SECRETARIAT", "student:read");
        RoleEntity rootRole = this.role("ESTABLISHMENT_ROOT", "user_access:write", "fee:read");
        this.admin = this.user(this.school, rootRole);
        this.accountant = this.user(this.school, accountantRole);
        this.otherAccountant = this.user(this.other, accountantRole);
    }

    @Test
    @DisplayName("l'école règle un rôle pour ses comptes, sans toucher aux autres écoles")
    void schoolSetsItsOwnRole() {
        this.login(this.admin);
        RoleSettingsDto settings = this.roleSettingsService.update("COMPTABLE", List.of("student:write"));
        assertThat(this.roleOf(settings, "COMPTABLE").getPermissions())
                .as("modifier implique consulter").containsExactlyInAnyOrder("student:write", "student:read");
        assertThat(this.roleOf(settings, "COMPTABLE").isCustomized()).isTrue();

        this.login(this.accountant);
        assertThat(this.currentUserProvider.hasPermission("student:write")).isTrue();
        assertThat(this.currentUserProvider.hasPermission("fee:read")).as("retiré par l'école").isFalse();

        this.login(this.otherAccountant);
        assertThat(this.currentUserProvider.hasPermission("fee:read")).as("l'autre école garde le défaut").isTrue();
        assertThat(this.currentUserProvider.hasPermission("student:write")).isFalse();

        this.login(this.admin);
        this.roleSettingsService.reset("COMPTABLE");
        this.login(this.accountant);
        assertThat(this.currentUserProvider.hasPermission("fee:read")).as("défaut rétabli").isTrue();
    }

    @Test
    @DisplayName("l'administrateur d'établissement ne se règle pas, et seul qui gère les accès règle")
    void lockedRoleAndManager() {
        this.login(this.admin);
        assertThatThrownBy(() -> this.roleSettingsService.update("ESTABLISHMENT_ROOT", List.of()))
                .isInstanceOf(BadRequestException.class);
        assertThat(this.roleOf(this.roleSettingsService.settings(), "ESTABLISHMENT_ROOT").isLocked()).isTrue();

        this.login(this.accountant);
        assertThatThrownBy(() -> this.roleSettingsService.update("COMPTABLE", List.of("settings:write")))
                .isInstanceOf(AccessDeniedException.class);
    }

    private RoleSettingsDto.Role roleOf(RoleSettingsDto settings, String code) {
        return settings.getRoles().stream().filter(r -> r.getCode().equals(code)).findFirst().orElseThrow();
    }

    private RoleEntity role(String code, String... permissions) {
        RoleEntity granted = roleWith(permissions);
        RoleEntity role = this.roleRepository.findByCode(code).orElseGet(() -> this.roleRepository.saveAndFlush(RoleEntity.builder()
                .code(code).name(TranslateEntity.builder().fr(code).en(code).build()).build()));
        role.getPermissions().clear();
        role.getPermissions().addAll(granted.getPermissions());
        return this.roleRepository.saveAndFlush(role);
    }

    private EstablishmentEntity school() {
        return this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
    }

    private String user(EstablishmentEntity establishment, RoleEntity role) {
        String email = "u-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(EstablishmentUserEntity.builder().role(role)
                .firstName("Awa").lastName("Traoré").establishment(establishment)
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
        return email;
    }

    private void login(String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
