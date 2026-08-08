package com.ypyit.neoelima.domain.prospect;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.domain.prospect.entity.DemoRequestStatus;
import com.ypyit.neoelima.domain.prospect.form.DemoRequestForm;
import com.ypyit.neoelima.domain.prospect.service.DemoRequestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demandes de démonstration.
 *
 * <p>Ce qui compte ici tient au fait que la route est <strong>ouverte</strong> : sans garde-fou,
 * la table se remplit toute seule et le suivi commercial devient inutilisable.
 */
class DemoRequestServiceTest extends AbstractIntegrationTest {

    @Autowired
    private DemoRequestService demoRequestService;

    @Test
    @DisplayName("une demande est enregistrée, et l'effectif déclaré propose une formule")
    void aRequestIsStoredAndSuggestsAPlan() {
        String school = "École " + UUID.randomUUID();
        this.demoRequestService.submit(this.form(school, "contact-" + UUID.randomUUID() + "@ecole.ci", 250));

        assertThat(this.demoRequestService.all())
                .filteredOn(row -> school.equals(row.getSchoolName()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getStatus()).isEqualTo(DemoRequestStatus.PENDING);
                    // 250 élèves : le palier Standard, proposé et non imposé.
                    assertThat(row.getSuggestedPlan()).isEqualTo("STANDARD");
                    assertThat(row.getHandledAt()).isNull();
                });
    }

    @Test
    @DisplayName("le pot de miel écarte l'automate, sans le lui dire")
    void theHoneypotDropsBots() {
        String school = "École " + UUID.randomUUID();
        DemoRequestForm form = this.form(school, "bot-" + UUID.randomUUID() + "@ecole.ci", 100);
        form.setWebsite("http://spam.example");

        // Aucune exception : un refus explicite apprendrait à l'automate à contourner le champ.
        this.demoRequestService.submit(form);

        assertThat(this.demoRequestService.all())
                .noneMatch(row -> school.equals(row.getSchoolName()));
    }

    @Test
    @DisplayName("une même adresse ne dépose pas dix demandes dans l'heure")
    void theSameAddressIsThrottled() {
        String email = "spam-" + UUID.randomUUID() + "@ecole.ci";
        String school = "École " + UUID.randomUUID();
        for (int i = 0; i < 6; i++) {
            this.demoRequestService.submit(this.form(school, email, 100));
        }

        assertThat(this.demoRequestService.all())
                .filteredOn(row -> school.equals(row.getSchoolName()))
                .hasSize(3);
    }

    @Test
    @DisplayName("marquer traitée date le traitement, revenir en attente efface la date")
    void handlingIsDatedAndReversible() {
        String school = "École " + UUID.randomUUID();
        this.demoRequestService.submit(this.form(school, "dir-" + UUID.randomUUID() + "@ecole.ci", 80));
        UUID id = this.demoRequestService.all().stream()
                .filter(row -> school.equals(row.getSchoolName()))
                .findFirst().orElseThrow().getId();

        assertThat(this.demoRequestService.setStatus(id, DemoRequestStatus.HANDLED, "Rappelée le 5"))
                .satisfies(row -> {
                    assertThat(row.getHandledAt()).isNotNull();
                    assertThat(row.getHandledNote()).isEqualTo("Rappelée le 5");
                });

        // Sans quoi la fiche dirait « traitée le 5 » d'une demande qu'on vient de rouvrir.
        assertThat(this.demoRequestService.setStatus(id, DemoRequestStatus.PENDING, null).getHandledAt())
                .isNull();
    }

    private DemoRequestForm form(String school, String email, Integer students) {
        DemoRequestForm form = new DemoRequestForm();
        form.setSchoolName(school);
        form.setContactName("Awa Traoré");
        form.setEmail(email);
        form.setCity("Abidjan");
        form.setStudentCount(students);
        form.setSourcePage("/");
        return form;
    }
}
