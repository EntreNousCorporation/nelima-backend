package com.ypyit.neoelima.security;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.domain.authentication.service.AuthService;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.user.entity.AdminUserEntity;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les routes d'abonnement sont fermées à tout ce qui n'est pas YPYit.
 *
 * <p>Une école qui y accéderait lirait les tarifs consentis à ses concurrentes et le chiffre
 * d'affaires que YPYit tire du parc. Un parent aussi.
 *
 * <p>Le cas passe par la <strong>vraie chaîne de filtres</strong>, avec un jeton réel, et sur les
 * quatre verbes. C'est délibéré : la faille déjà rencontrée sur {@code /establishments} ne venait
 * pas d'une règle absente mais d'une règle posée sur le seul GET, les écritures retombant sur le
 * simple « être authentifié ». Une vérification qui n'aurait éprouvé que la lecture l'aurait
 * manquée, et un test du gestionnaire d'autorisation seul n'aurait rien dit du câblage.
 */
@AutoConfigureMockMvc
class SubscriptionRouteClosureTest extends AbstractIntegrationTest {

    private static final List<String> PATHS = List.of(
            "/subscriptions/plans",
            "/subscriptions/invoices",
            "/subscriptions/due",
            // Les prospects sont couverts par le même verrou : une école y lirait le nom, le
            // téléphone et l'effectif des directeurs que YPYit démarche.
            "/prospects");

    private static final List<HttpMethod> METHODS = List.of(
            HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Test
    @DisplayName("une école est refusée sur toutes les routes d'abonnement, quel que soit le verbe")
    void aSchoolIsRefusedOnEveryMethod() throws Exception {
        String token = this.tokenOfSchoolAccount();

        for (String path : PATHS) {
            for (HttpMethod method : METHODS) {
                int status = this.call(token, method, path);
                assertThat(status)
                        .as("%s %s doit être refusé à une école (reçu %d)", method, path, status)
                        .isIn(401, 403);
            }
        }
    }

    @Test
    @DisplayName("un parent n'y accède pas davantage")
    void aParentIsRefused() throws Exception {
        String token = this.tokenOfParentAccount();

        assertThat(this.call(token, HttpMethod.GET, "/subscriptions/invoices")).isIn(401, 403);
        assertThat(this.call(token, HttpMethod.POST, "/subscriptions/invoices")).isIn(401, 403);
    }

    @Test
    @DisplayName("YPYit accède à la grille, sinon la console ne servirait à rien")
    void thePlatformAdminIsAllowed() throws Exception {
        String token = this.tokenOfAdminAccount();

        assertThat(this.call(token, HttpMethod.GET, "/subscriptions/plans")).isEqualTo(200);
        assertThat(this.call(token, HttpMethod.GET, "/subscriptions/invoices")).isEqualTo(200);
        assertThat(this.call(token, HttpMethod.GET, "/subscriptions/due")).isEqualTo(200);
        assertThat(this.call(token, HttpMethod.GET, "/prospects")).isEqualTo(200);
    }

    /* ---------- outillage ---------- */

    private int call(String token, HttpMethod method, String path) throws Exception {
        return this.mockMvc.perform(MockMvcRequestBuilders.request(method, path)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{}"))
                .andReturn().getResponse().getStatus();
    }

    private String tokenOfSchoolAccount() {
        EstablishmentEntity school = this.establishmentRepository.saveAndFlush(
                EstablishmentEntity.builder()
                        .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
        String email = "direction-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(school)
                .contacts(primaryEmail(email)).build());
        return this.authService.generateToken(email).getAccessToken();
    }

    private String tokenOfParentAccount() {
        String email = "parent-" + UUID.randomUUID() + "@gmail.com";
        this.userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName("Jean").lastName("Kouassi").contacts(primaryEmail(email)).build());
        return this.authService.generateToken(email).getAccessToken();
    }

    private String tokenOfAdminAccount() {
        String email = "ops-" + UUID.randomUUID() + "@nelima.ci";
        this.userRepository.saveAndFlush(AdminUserEntity.builder()
                .firstName("Emmanuel").lastName("Koffi").contacts(primaryEmail(email)).build());
        return this.authService.generateToken(email).getAccessToken();
    }

    private static Set<ContactEntity> primaryEmail(String email) {
        return new HashSet<>(Set.of(ContactEntity.builder()
                .type(ContactType.EMAIL).value(email).isPrimary(true).build()));
    }
}
