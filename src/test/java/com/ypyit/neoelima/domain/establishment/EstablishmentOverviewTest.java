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
 * La page école de la console : comptes, personnel, niveaux et classes d'une école.
 *
 * <p>Par la vraie chaîne de filtres : la route n'est gardée que par
 * {@code PLATFORM_CONSOLE_RESOURCES}, et elle expose les comptes et le personnel d'une école.
 */
@AutoConfigureMockMvc
class EstablishmentOverviewTest extends AbstractIntegrationTest {

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
    @DisplayName("une école ne lit pas la composition d'une école, même la sienne")
    void aSchoolIsRefused() throws Exception {
        EstablishmentEntity school = this.school();
        String email = "direction-" + UUID.randomUUID() + "@ecole.ci";
        this.director(school, email);

        int status = this.get(this.authService.generateToken(email).getAccessToken(), school.getId())
                .getResponse().getStatus();

        assertThat(status).isIn(401, 403);
    }

    @Test
    @DisplayName("le directeur en tête des comptes, encore à activer tant que le lien n'a pas servi")
    void thePrincipalComesFirst() throws Exception {
        EstablishmentEntity school = this.school();
        UUID principal = this.director(school, "direction-" + UUID.randomUUID() + "@ecole.ci");
        this.jdbcTemplate.update("update establishment set principal_id = ? where id = ?", principal, school.getId());
        this.director(school, "aaa-secretariat-" + UUID.randomUUID() + "@ecole.ci");

        JsonNode accounts = this.overviewOf(school).get("accounts");

        assertThat(accounts).hasSize(2);
        assertThat(accounts.get(0).get("id").asText()).isEqualTo(principal.toString());
        assertThat(accounts.get(0).get("principal").asBoolean()).isTrue();
        assertThat(accounts.get(0).get("activated").asBoolean()).isFalse();
        assertThat(accounts.get(0).get("email").asText()).startsWith("direction-");
    }

    @Test
    @DisplayName("le personnel et les classes, avec effectifs et professeur principal — jamais les salaires")
    void staffAndClassesWithoutSalaries() throws Exception {
        EstablishmentEntity school = this.school();
        UUID teacher = UUID.randomUUID();
        this.jdbcTemplate.update("""
                insert into staff (id, first_name, last_name, role, job_title, monthly_salary, active, establishment_id)
                values (?, 'Awa', 'Traoré', 'TEACHER', 'Professeure de français', 450000, true, ?)
                """, teacher, school.getId());
        UUID level = UUID.randomUUID();
        String code = "QUATRIEME-" + UUID.randomUUID().toString().substring(0, 6);
        this.jdbcTemplate.update("insert into level_of_study (id, code, position) values (?, ?, 12)", level, code);
        this.jdbcTemplate.update("insert into establishment_level_of_studies (establishment_id, level_of_study_id) values (?, ?)",
                school.getId(), level);
        this.jdbcTemplate.update("""
                insert into school_class (id, name, capacity, establishment_id, level_of_study_id, main_teacher_id)
                values (?, '4EME A', 40, ?, ?, ?)
                """, UUID.randomUUID(), school.getId(), level, teacher);

        MvcResult result = this.get(this.tokenOfAdmin(), school.getId());
        String body = result.getResponse().getContentAsString();
        JsonNode overview = this.objectMapper.readTree(body);

        assertThat(body).doesNotContain("450000").doesNotContainIgnoringCase("salary");
        JsonNode staff = overview.get("staff").get(0);
        assertThat(staff.get("lastName").asText()).isEqualTo("Traoré");
        assertThat(staff.get("mainTeacherOf").get(0).asText()).isEqualTo("4EME A");
        JsonNode schoolClass = overview.get("levels").get(0).get("classes").get(0);
        assertThat(schoolClass.get("name").asText()).isEqualTo("4EME A");
        assertThat(schoolClass.get("mainTeacher").asText()).isEqualTo("Awa Traoré");
        assertThat(schoolClass.get("capacity").asInt()).isEqualTo(40);
        assertThat(schoolClass.get("students").asLong()).isZero();
    }

    @Test
    @DisplayName("une école inconnue répond 404")
    void unknownSchool() throws Exception {
        assertThat(this.get(this.tokenOfAdmin(), UUID.randomUUID()).getResponse().getStatus()).isEqualTo(404);
    }

    /* ---------- outillage ---------- */

    private JsonNode overviewOf(EstablishmentEntity school) throws Exception {
        MvcResult result = this.get(this.tokenOfAdmin(), school.getId());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return this.objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult get(String token, UUID id) throws Exception {
        return this.mockMvc.perform(MockMvcRequestBuilders.get("/admin/establishments/" + id + "/overview")
                .header("Authorization", "Bearer " + token)).andReturn();
    }

    private EstablishmentEntity school() {
        return this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
    }

    private UUID director(EstablishmentEntity school, String email) {
        return this.userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Serge").lastName("Kouadio").establishment(school)
                .contacts(primaryEmail(email)).build()).getId();
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
