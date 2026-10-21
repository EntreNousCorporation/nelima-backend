package com.ypyit.neoelima.domain.security;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lire un compte par son identifiant : le sien oui, celui d'un autre non. */
@Transactional
class AccountReadAccessTest extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EstablishmentRepository establishmentRepository;

    private UserEntity parent;
    private UserEntity secretary;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        EstablishmentEntity school = this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
        this.parent = this.user(StudentParentUserEntity.builder());
        this.secretary = this.user(EstablishmentUserEntity.builder().establishment(school));
    }

    @Test
    @DisplayName("un compte lit le sien, pas celui d'un autre")
    void onlyOwnAccount() {
        this.login(this.parent);
        assertThat(this.userService.getMe(this.parent.getId()).getId()).isEqualTo(this.parent.getId());
        assertThatThrownBy(() -> this.userService.getMe(this.secretary.getId())).isInstanceOf(AccessDeniedException.class);

        this.login(this.secretary);
        assertThatThrownBy(() -> this.userService.getMe(this.parent.getId())).isInstanceOf(AccessDeniedException.class);
    }

    private UserEntity user(UserEntity.UserEntityBuilder<?, ?> builder) {
        String email = "u-" + UUID.randomUUID() + "@ecole.ci";
        return this.userRepository.saveAndFlush(builder.firstName("Awa").lastName("Traoré")
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
    }

    private void login(UserEntity user) {
        String email = user.getContacts().iterator().next().getValue();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
