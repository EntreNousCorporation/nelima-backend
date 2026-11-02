package com.ypyit.neoelima.domain.establishment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.domain.authentication.service.AuthService;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.user.entity.AdminUserEntity;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Avancement de la configuration d'une école, sur la fiche de la console YPYit.
 *
 * <p>Passe par la vraie chaîne de filtres : la route n'est gardée que par
 * {@code PLATFORM_CONSOLE_RESOURCES}, un oubli de câblage l'ouvrirait à toute école connectée.
 */
@AutoConfigureMockMvc
class EstablishmentSetupTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("une école ne lit pas l'avancement, même le sien")
    void aSchoolIsRefused() throws Exception {
        EstablishmentEntity school = this.school();
        String token = this.tokenOfDirector(school);

        int status = this.get(token, school.getId()).getResponse().getStatus();

        assertThat(status).isIn(401, 403);
    }

    @Test
    @DisplayName("une école neuve n'a rien de fait, et la fiche dit ce qui manque")
    void aNewSchoolStartsAtZero() throws Exception {
        EstablishmentEntity school = this.school();

        JsonNode setup = this.setupOf(school);

        assertThat(setup.get("progress").asInt()).isZero();
        assertThat(setup.get("total").asInt()).isEqualTo(setup.get("steps").size());
        assertThat(this.step(setup, "identity").get("detail").asText()).contains("logo", "téléphone");
        assertThat(this.step(setup, "principal").get("detail").asText()).isEqualTo("Aucun directeur désigné");
        assertThat(this.step(setup, "subscription").get("done").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("une classe et une formule font avancer la progression, chiffres à l'appui")
    void stepsFollowTheData() throws Exception {
        EstablishmentEntity school = this.school();
        this.jdbcTemplate.update("insert into school_class (id, name, capacity, establishment_id) values (?, ?, 40, ?)",
                UUID.randomUUID(), "CM2 A", school.getId());
        this.jdbcTemplate.update("update establishment set subscription_plan = 'STANDARD' where id = ?", school.getId());

        JsonNode setup = this.setupOf(school);

        assertThat(this.step(setup, "classes").get("done").asBoolean()).isTrue();
        assertThat(this.step(setup, "classes").get("detail").asText()).isEqualTo("1 classe");
        assertThat(this.step(setup, "subscription").get("done").asBoolean()).isTrue();
        assertThat(setup.get("done").asInt()).isEqualTo(2);
        assertThat(setup.get("progress").asInt())
                .isEqualTo(Math.round(100f * 2 / setup.get("total").asInt()));
    }

    @Test
    @DisplayName("la liste du parc est fermée aux écoles")
    void theListIsRefusedToSchools() throws Exception {
        String token = this.tokenOfDirector(this.school());

        int status = this.mockMvc.perform(MockMvcRequestBuilders.get("/admin/establishments/setup")
                .header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
    }

    @Test
    @DisplayName("la liste donne, pour chaque école, le pourcentage de sa fiche")
    void theListMatchesTheSheet() throws Exception {
        EstablishmentEntity school = this.school();
        this.jdbcTemplate.update("insert into school_class (id, name, capacity, establishment_id) values (?, ?, 40, ?)",
                UUID.randomUUID(), "6e A", school.getId());

        JsonNode sheet = this.setupOf(school);
        MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.get("/admin/establishments/setup")
                .header("Authorization", "Bearer " + this.tokenOfAdmin())).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode row = null;
        for (JsonNode entry : this.objectMapper.readTree(result.getResponse().getContentAsString())) {
            if (school.getId().toString().equals(entry.get("establishmentId").asText())) {
                row = entry;
            }
        }

        assertThat(row).as("l'école doit figurer dans la liste").isNotNull();
        assertThat(row.get("progress").asInt()).isEqualTo(sheet.get("progress").asInt());
        assertThat(row.get("done").asInt()).isEqualTo(sheet.get("done").asInt()).isEqualTo(1);
    }

    /* ---------- outillage ---------- */

    private JsonNode setupOf(EstablishmentEntity school) throws Exception {
        MvcResult result = this.get(this.tokenOfAdmin(), school.getId());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return this.objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode step(JsonNode setup, String key) {
        for (JsonNode step : setup.get("steps")) {
            if (key.equals(step.get("key").asText())) {
                return step;
            }
        }
        throw new AssertionError("étape absente : " + key);
    }

    private MvcResult get(String token, UUID id) throws Exception {
        return this.mockMvc.perform(MockMvcRequestBuilders.get("/admin/establishments/" + id + "/setup")
                .header("Authorization", "Bearer " + token)).andReturn();
    }

    private EstablishmentEntity school() {
        return this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
    }

    private String tokenOfDirector(EstablishmentEntity school) {
        String email = "direction-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré").establishment(school)
                .contacts(primaryEmail(email)).build());
        return this.authService.generateToken(email).getAccessToken();
    }

    private String tokenOfAdmin() {
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
