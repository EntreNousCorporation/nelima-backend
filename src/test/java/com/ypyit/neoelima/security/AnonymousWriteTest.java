package com.ypyit.neoelima.security;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.UnAuthenticatedUserException;
import com.ypyit.neoelima.config.security.ClosedYearWriteInterceptor;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Écritures publiques.
 *
 * <p>Spring place un {@link AnonymousAuthenticationToken} (nom {@code anonymousUser}) sur toute
 * requête sans jeton. Le traiter comme un appelant connu faisait chercher {@code anonymousUser} en
 * base : le ClosedYearWriteInterceptor répondait 404 à la définition du mot de passe d'un nouveau
 * directeur, à la demande de démo, aux webhooks de paiement.
 */
class AnonymousWriteTest extends AbstractIntegrationTest {

    @Autowired
    private CurrentUserProvider currentUserProvider;
    @Autowired
    private ClosedYearWriteInterceptor closedYearWriteInterceptor;

    @BeforeEach
    void anonymous() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("L'utilisateur anonyme n'est pas un appelant : pas de recherche en base")
    void anonymousIsNotACaller() {
        assertThatThrownBy(() -> this.currentUserProvider.currentUser())
                .isInstanceOf(UnAuthenticatedUserException.class);
    }

    @Test
    @DisplayName("Définir son mot de passe sans être connecté passe le contrôle d'année clôturée")
    void resetPasswordIsNotBlocked() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/users/reset-password");
        assertThat(this.closedYearWriteInterceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isTrue();
    }
}
