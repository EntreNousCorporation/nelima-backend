package com.ypyit.neoelima.security;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'appelant est résolu une fois par requête HTTP.
 *
 * <p>Le tableau de bord appelait currentUser() une fois par agrégat : autant de requêtes SQL pour
 * relire le même compte. Mémorisé le temps de la requête, il ne doit jamais pour autant survivre
 * à un changement d'identité, ni se mémoriser hors requête web.
 */
@Transactional
class CurrentUserRequestCacheTest extends AbstractIntegrationTest {

    @Autowired
    private CurrentUserProvider currentUserProvider;
    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Dans une requête, le même appelant est réutilisé")
    void sameCallerIsReused() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        authenticate(this.parent());

        UserEntity first = this.currentUserProvider.currentUser();

        assertThat(this.currentUserProvider.currentUser()).isSameAs(first);
    }

    @Test
    @DisplayName("Un changement d'identité dans la requête n'hérite pas de l'appelant précédent")
    void identityChangeIsHonoured() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        String first = this.parent();
        String second = this.parent();

        authenticate(first);
        UserEntity a = this.currentUserProvider.currentUser();
        authenticate(second);
        UserEntity b = this.currentUserProvider.currentUser();

        assertThat(b.getId()).isNotEqualTo(a.getId());
    }

    @Test
    @DisplayName("Hors requête web, l'appelant est résolu à chaque appel")
    void nothingIsCachedOutsideAWebRequest() {
        // Le contexte de test attache une requête simulée : on s'en défait pour se placer
        // comme une tâche planifiée.
        RequestContextHolder.resetRequestAttributes();
        authenticate(this.parent());

        UserEntity first = this.currentUserProvider.currentUser();

        assertThat(this.currentUserProvider.currentUser().getId()).isEqualTo(first.getId());
        assertThat(RequestContextHolder.getRequestAttributes()).isNull();
    }

    private String parent() {
        String email = "parent-" + UUID.randomUUID() + "@gmail.com";
        this.userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName("Jean").lastName("Kouassi")
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
        return email;
    }

    private static void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", List.of()));
    }
}
