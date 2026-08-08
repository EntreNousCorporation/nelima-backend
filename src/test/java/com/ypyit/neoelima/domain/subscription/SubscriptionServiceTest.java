package com.ypyit.neoelima.domain.subscription;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.subscription.dto.SubscriptionDueDto;
import com.ypyit.neoelima.domain.subscription.dto.SubscriptionInvoiceDto;
import com.ypyit.neoelima.domain.subscription.dto.SubscriptionPlanDto;
import com.ypyit.neoelima.domain.subscription.form.SubscriptionPlanForm;
import com.ypyit.neoelima.domain.subscription.service.SubscriptionPlanService;
import com.ypyit.neoelima.domain.subscription.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Abonnement des écoles à Nelima.
 *
 * <p>Ce qui est éprouvé ici tient en une phrase : une facture est une pièce. Son montant ne bouge
 * plus, son numéro ne se répète pas, et son état se déduit plutôt qu'il ne se stocke. Un défaut sur
 * l'un de ces trois points ne se voit pas à l'écran — il se découvre à la clôture comptable.
 */
@Transactional
class SubscriptionServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SubscriptionService subscriptionService;
    @Autowired
    private SubscriptionPlanService planService;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private StudentRepository studentRepository;

    private EstablishmentEntity school;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.school = this.school();
    }

    @Test
    @DisplayName("le palier suit l'effectif, mais la formule souscrite prime")
    void suggestedPlanNeverOverridesTheSubscribedOne() {
        assertThat(this.planService.suggestedFor(0)).isEqualTo("DECOUVERTE");
        assertThat(this.planService.suggestedFor(100)).isEqualTo("DECOUVERTE");
        assertThat(this.planService.suggestedFor(101)).isEqualTo("STANDARD");
        assertThat(this.planService.suggestedFor(1001)).isEqualTo("ENTERPRISE");

        // Une école de 3 élèves à qui l'on a consenti la formule Pro la garde : l'effectif propose,
        // il ne corrige pas un contrat.
        this.subscriptionService.subscribe(this.school.getId(), "PRO",
                LocalDate.now().minusYears(1).minusDays(1));
        this.students(3);

        SubscriptionDueDto due = this.subscriptionService.due().stream()
                .filter(row -> row.getEstablishmentId().equals(this.school.getId().toString()))
                .findFirst().orElseThrow();

        assertThat(due.getPlan()).isEqualTo("PRO");
        // L'écart est signalé, pas appliqué.
        assertThat(due.getSuggestedPlan()).isEqualTo("DECOUVERTE");
    }

    @Test
    @DisplayName("une facture émise fige son montant : changer le tarif ne la modifie pas")
    void anIssuedInvoiceFreezesItsAmount() {
        this.subscriptionService.subscribe(this.school.getId(), "STANDARD",
                LocalDate.now().minusYears(1).minusDays(1));
        SubscriptionInvoiceDto invoice = this.subscriptionService.issue(this.school.getId());
        BigDecimal issued = invoice.getAmount();

        this.setPrice("STANDARD", new BigDecimal("999999"));

        // Renégocier un tarif ne doit pas réécrire le chiffre d'affaires des mois passés.
        assertThat(this.subscriptionService.invoicesOf(this.school.getId()))
                .singleElement()
                .satisfies(row -> assertThat(row.getAmount()).isEqualByComparingTo(issued));
    }

    @Test
    @DisplayName("la même période ne se facture pas deux fois")
    void refusesToBillTheSamePeriodTwice() {
        this.subscriptionService.subscribe(this.school.getId(), "DECOUVERTE",
                LocalDate.now().minusYears(1).minusDays(1));
        this.subscriptionService.issue(this.school.getId());

        // Sans ce garde-fou, la dette de l'école doublerait sans que personne ne s'en aperçoive.
        assertThatThrownBy(() -> this.subscriptionService.issue(this.school.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("la numérotation est continue et sans doublon")
    void numbersAreSequential() {
        EstablishmentEntity other = this.school();
        this.subscriptionService.subscribe(this.school.getId(), "DECOUVERTE", LocalDate.now());
        this.subscriptionService.subscribe(other.getId(), "PRO", LocalDate.now());

        String first = this.subscriptionService.issue(this.school.getId()).getNumber();
        String second = this.subscriptionService.issue(other.getId()).getNumber();

        assertThat(first).matches("NL-\\d{4}-\\d{4}");
        assertThat(second).isNotEqualTo(first);
        assertThat(Integer.parseInt(second.substring(second.length() - 4)))
                .isEqualTo(Integer.parseInt(first.substring(first.length() - 4)) + 1);
    }

    @Test
    @DisplayName("« en retard » se déduit de l'échéance, jamais d'une colonne")
    void lateIsDerived() {
        this.subscriptionService.subscribe(this.school.getId(), "DECOUVERTE", LocalDate.now());
        SubscriptionInvoiceDto issued = this.subscriptionService.issue(this.school.getId());

        // Émise le jour même : trente jours de délai, donc pas encore en retard.
        assertThat(issued.getStatus()).isEqualTo("ISSUED");

        SubscriptionInvoiceDto paid = this.subscriptionService.recordPayment(
                UUID.fromString(issued.getId()), LocalDate.now(), "Virement", "VIR-2026-14");

        assertThat(paid.getStatus()).isEqualTo("PAID");
        assertThat(paid.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("une facture réglée ne s'annule pas, une facture annulée ne se règle pas")
    void paidAndCancelledAreTerminal() {
        this.subscriptionService.subscribe(this.school.getId(), "DECOUVERTE", LocalDate.now());
        SubscriptionInvoiceDto invoice = this.subscriptionService.issue(this.school.getId());
        UUID id = UUID.fromString(invoice.getId());

        this.subscriptionService.recordPayment(id, LocalDate.now(), "Espèces", null);
        assertThatThrownBy(() -> this.subscriptionService.cancel(id, "erreur"))
                .isInstanceOf(BadRequestException.class);

        EstablishmentEntity other = this.school();
        this.subscriptionService.subscribe(other.getId(), "PRO", LocalDate.now());
        SubscriptionInvoiceDto second = this.subscriptionService.issue(other.getId());
        UUID secondId = UUID.fromString(second.getId());
        this.subscriptionService.cancel(secondId, "émise par erreur");

        assertThat(this.subscriptionService.invoicesOf(other.getId()))
                .singleElement()
                .satisfies(row -> assertThat(row.getStatus()).isEqualTo("CANCELLED"));
        assertThatThrownBy(() -> this.subscriptionService.recordPayment(
                secondId, LocalDate.now(), "Virement", null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("annuler une facture rouvre sa période, sous un nouveau numéro")
    void cancellingFreesThePeriod() {
        this.subscriptionService.subscribe(this.school.getId(), "STANDARD", LocalDate.now());
        SubscriptionInvoiceDto wrong = this.subscriptionService.issue(this.school.getId());
        this.subscriptionService.cancel(UUID.fromString(wrong.getId()), "formule erronée");

        // Sans quoi l'annulation serait un piège : la facture fautive disparaîtrait des comptes,
        // et la période resterait impossible à refacturer — l'école ne serait jamais appelée.
        assertThat(this.subscriptionService.due())
                .extracting(SubscriptionDueDto::getEstablishmentId)
                .contains(this.school.getId().toString());

        SubscriptionInvoiceDto right = this.subscriptionService.issue(this.school.getId());
        assertThat(right.getNumber()).isNotEqualTo(wrong.getNumber());
        assertThat(right.getPeriodStart()).isEqualTo(wrong.getPeriodStart());

        // La fautive reste : c'est ce qui garde la numérotation continue, donc justifiable.
        assertThat(this.subscriptionService.invoicesOf(this.school.getId())).hasSize(2);
    }

    @Test
    @DisplayName("une seule formule est mise en avant, et retirer du site retire la mise en avant")
    void onlyOnePlanIsFeatured() {
        var grid = this.planService.list(true);
        var standard = grid.stream().filter(r -> "STANDARD".equals(r.getPlan())).findFirst().orElseThrow();
        var pro = grid.stream().filter(r -> "PRO".equals(r.getPlan())).findFirst().orElseThrow();

        this.planService.update(standard.getId(), this.formOf(standard, true, true));
        this.planService.update(pro.getId(), this.formOf(pro, true, true));

        // Basculer la mise en avant doit la retirer à l'autre, sinon le site en afficherait deux.
        assertThat(this.planService.list(true))
                .filteredOn(SubscriptionPlanDto::isFeatured)
                .singleElement()
                .satisfies(row -> assertThat(row.getPlan()).isEqualTo("PRO"));

        // Retirée du site, une formule ne peut plus y être mise en avant : la carte n'existerait pas.
        this.planService.update(pro.getId(), this.formOf(pro, false, true));
        assertThat(this.planService.list(true)).noneMatch(SubscriptionPlanDto::isFeatured);
        assertThat(this.planService.publicGrid()).noneMatch(row -> "PRO".equals(row.getPlan()));
    }

    @Test
    @DisplayName("la grille publique ne rend ni identifiant ni compteur")
    void thePublicGridIsStripped() {
        var standard = this.planService.list(true).stream()
                .filter(r -> "STANDARD".equals(r.getPlan())).findFirst().orElseThrow();
        this.planService.update(standard.getId(), this.formOf(standard, true, false));

        assertThat(this.planService.publicGrid())
                .filteredOn(row -> "STANDARD".equals(row.getPlan()))
                .singleElement()
                .satisfies(row -> {
                    // Publier combien d'écoles ont souscrit à chaque palier reviendrait à publier
                    // la structure du chiffre d'affaires de YPYit.
                    assertThat(row.getId()).isNull();
                    assertThat(row.getEstablishmentCount()).isZero();
                    assertThat(row.getInvoiceCount()).isZero();
                    assertThat(row.getPrice()).isNotNull();
                });
    }

    @Test
    @DisplayName("une école sans formule ne casse ni le parc ni la facturation")
    void aSchoolWithoutAPlanIsSimplyAbsent() {
        // Aucune souscription : l'école existe, elle n'est simplement pas à facturer.
        assertThat(this.subscriptionService.due())
                .noneMatch(row -> row.getEstablishmentId().equals(this.school.getId().toString()));

        assertThatThrownBy(() -> this.subscriptionService.issue(this.school.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("la période suit l'anniversaire de la souscription")
    void periodFollowsTheSubscriptionAnniversary() {
        LocalDate subscribed = LocalDate.of(2025, 3, 14);

        assertThat(this.subscriptionService.currentPeriodStart(subscribed, LocalDate.of(2025, 6, 1)))
                .isEqualTo(subscribed);
        assertThat(this.subscriptionService.currentPeriodStart(subscribed, LocalDate.of(2026, 3, 13)))
                .isEqualTo(subscribed);
        assertThat(this.subscriptionService.currentPeriodStart(subscribed, LocalDate.of(2026, 3, 14)))
                .isEqualTo(LocalDate.of(2026, 3, 14));
    }

    @Test
    @DisplayName("la grille vit en base : créer et renommer une formule ne demande rien de plus")
    void theGridLivesInTheDatabase() {
        assertThat(this.subscriptionService.priceOf("DECOUVERTE"))
                .isEqualByComparingTo(new BigDecimal("75000"));
        this.setPrice("DECOUVERTE", new BigDecimal("90000"));
        assertThat(this.subscriptionService.priceOf("DECOUVERTE"))
                .isEqualByComparingTo(new BigDecimal("90000"));

        // Une formule créée depuis la console est immédiatement souscriptible : c'est tout l'objet
        // de la sortir du code.
        SubscriptionPlanForm form = new SubscriptionPlanForm();
        form.setLabel("Offre de lancement");
        form.setMaxStudents(50);
        form.setPrice(new BigDecimal("40000"));
        var created = this.planService.create(form);
        assertThat(created.getPlan()).isEqualTo("OFFRE_DE_LANCEMENT");

        this.subscriptionService.subscribe(this.school.getId(), created.getPlan(),
                LocalDate.now().minusYears(1).minusDays(1));
        assertThat(this.subscriptionService.issue(this.school.getId()).getAmount())
                .isEqualByComparingTo(new BigDecimal("40000"));

        // Utilisée, elle ne se supprime plus : la facture désignerait une formule disparue.
        assertThatThrownBy(() -> this.planService.delete(created.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("renommer une formule ne réécrit pas les factures déjà émises")
    void renamingAPlanLeavesIssuedInvoicesAlone() {
        this.subscriptionService.subscribe(this.school.getId(), "PRO",
                LocalDate.now().minusYears(1).minusDays(1));
        this.subscriptionService.issue(this.school.getId());

        var pro = this.planService.list(true).stream()
                .filter(row -> "PRO".equals(row.getPlan())).findFirst().orElseThrow();
        SubscriptionPlanForm form = new SubscriptionPlanForm();
        form.setLabel("Pro renommée");
        form.setMaxStudents(pro.getMaxStudents());
        form.setPrice(pro.getPrice());
        this.planService.update(pro.getId(), form);

        // Le libellé est recopié à l'émission : une pièce comptable ne se réécrit pas.
        assertThat(this.subscriptionService.invoicesOf(this.school.getId()))
                .singleElement()
                .satisfies(row -> assertThat(row.getPlanLabel()).isEqualTo("Pro"));
    }

    /* ---------- outillage ---------- */

    private SubscriptionPlanForm formOf(SubscriptionPlanDto plan, boolean isPublic, boolean featured) {
        SubscriptionPlanForm form = new SubscriptionPlanForm();
        form.setLabel(plan.getLabel());
        form.setMaxStudents(plan.getMaxStudents());
        form.setPrice(plan.getPrice());
        form.setIsPublic(isPublic);
        form.setFeatured(featured);
        return form;
    }

    private void setPrice(String code, BigDecimal price) {
        var plan = this.planService.list(true).stream()
                .filter(row -> code.equals(row.getPlan())).findFirst().orElseThrow();
        SubscriptionPlanForm form = new SubscriptionPlanForm();
        form.setLabel(plan.getLabel());
        form.setMaxStudents(plan.getMaxStudents());
        form.setPrice(price);
        this.planService.update(plan.getId(), form);
    }

    /* ---------- fabriques ---------- */

    private EstablishmentEntity school() {
        return this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
    }

    private void students(int count) {
        for (int index = 0; index < count; index++) {
            this.studentRepository.saveAndFlush(StudentEntity.builder()
                    .firstName("Élève").lastName(String.valueOf(index))
                    .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                    .birthDay(LocalDate.of(2013, 5, 4)).establishment(this.school).build());
        }
    }
}
