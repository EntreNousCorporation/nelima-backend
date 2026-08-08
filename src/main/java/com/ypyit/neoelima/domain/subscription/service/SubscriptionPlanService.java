package com.ypyit.neoelima.domain.subscription.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.subscription.dto.SubscriptionPlanDto;
import com.ypyit.neoelima.domain.subscription.entity.SubscriptionPlanEntity;
import com.ypyit.neoelima.domain.subscription.form.SubscriptionPlanForm;
import com.ypyit.neoelima.domain.subscription.repository.SubscriptionInvoiceRepository;
import com.ypyit.neoelima.domain.subscription.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Grille des formules d'abonnement.
 *
 * <p>Elle vit en base et se règle depuis la console : créer une offre de lancement ou un contrat
 * cadre ne demande pas de livraison. Trois règles la tiennent.
 *
 * <p><strong>Le code ne change jamais.</strong> Il est dérivé du libellé à la création puis figé :
 * l'établissement et la facture le recopient, et le renommer leur ferait désigner une formule
 * disparue. Renommer une formule change son libellé, pas son code.
 *
 * <p><strong>On désactive plutôt que de supprimer</strong> dès qu'une école ou une facture s'y
 * réfère. Une grille où l'on efface laisse des contrats orphelins et des pièces comptables dont le
 * palier ne veut plus rien dire.
 *
 * <p><strong>Le palier propose, il n'impose pas.</strong> {@code suggestedFor} rend la formule que
 * l'effectif appellerait ; la formule souscrite prime toujours, car elle a pu être négociée.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionPlanService {

    /**
     * Borne haute de saisie.
     *
     * <p>Le plus cher des paliers actés vaut 600 000 F. Dix millions relèvent presque à coup sûr
     * d'une frappe en trop, et une facture émise à ce montant se découvrirait chez le client.
     */
    private static final BigDecimal MAX_PRICE = new BigDecimal("10000000");

    private static final int CODE_MAX_LENGTH = 32;

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionInvoiceRepository invoiceRepository;
    private final EstablishmentRepository establishmentRepository;

    /* ---------------- Lecture ---------------- */

    @Transactional(readOnly = true)
    public List<SubscriptionPlanDto> list(boolean includeInactive) {
        List<SubscriptionPlanEntity> plans = includeInactive
                ? this.planRepository.findByOrderByPositionAscLabelAsc()
                : this.planRepository.findByActiveTrueOrderByPositionAscLabelAsc();
        return plans.stream().map(this::toDto).toList();
    }

    /**
     * Grille du site public.
     *
     * <p>Seules les formules actives et explicitement publiées. Une offre négociée pour un réseau,
     * un tarif de lancement réservé aux pilotes n'ont rien à faire sur une page ouverte : le site
     * ne publie que ce que YPYit a coché.
     */
    @Transactional(readOnly = true)
    public List<SubscriptionPlanDto> publicGrid() {
        return this.planRepository.findByActiveTrueAndIsPublicTrueOrderByPositionAscLabelAsc()
                .stream().map(this::toPublicDto).toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionPlanEntity byCode(String code) {
        return this.planRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Formule d'abonnement introuvable : " + code));
    }

    /**
     * Formule que l'effectif appellerait.
     *
     * <p>La plus petite formule active dont le plafond couvre l'effectif ; à défaut, celle sans
     * plafond. Nulle si la grille n'en propose aucune — mieux vaut ne rien suggérer que suggérer
     * la mauvaise.
     */
    @Transactional(readOnly = true)
    public String suggestedFor(long studentCount) {
        List<SubscriptionPlanEntity> active = this.planRepository
                .findByActiveTrueOrderByPositionAscLabelAsc();

        return active.stream()
                .filter(plan -> Objects.nonNull(plan.getMaxStudents()))
                .filter(plan -> plan.covers(studentCount))
                .min(Comparator.comparingInt(SubscriptionPlanEntity::getMaxStudents))
                .or(() -> active.stream()
                        .filter(plan -> Objects.isNull(plan.getMaxStudents()))
                        .findFirst())
                .map(SubscriptionPlanEntity::getCode)
                .orElse(null);
    }

    /* ---------------- Écriture ---------------- */

    @Transactional
    public SubscriptionPlanDto create(SubscriptionPlanForm form) {
        this.checkPrice(form.getPrice());

        String code = this.allocateCode(form.getLabel());
        SubscriptionPlanEntity plan = this.planRepository.saveAndFlush(SubscriptionPlanEntity.builder()
                .code(code)
                .label(form.getLabel().trim())
                .description(this.trimmed(form.getDescription()))
                .maxStudents(form.getMaxStudents())
                .price(form.getPrice())
                .active(!Boolean.FALSE.equals(form.getActive()))
                .position(Objects.requireNonNullElseGet(form.getPosition(), this::nextPosition))
                .isPublic(Boolean.TRUE.equals(form.getIsPublic()))
                .build());

        if (Boolean.TRUE.equals(form.getFeatured())) this.feature(plan);

        log.info("SUBSCRIPTION_PLAN_CREATED: {} ({} FCFA)", code, form.getPrice());
        return this.toDto(plan);
    }

    @Transactional
    public SubscriptionPlanDto update(UUID id, SubscriptionPlanForm form) {
        this.checkPrice(form.getPrice());

        SubscriptionPlanEntity plan = this.planRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Formule d'abonnement introuvable."));

        plan.setLabel(form.getLabel().trim());
        plan.setDescription(this.trimmed(form.getDescription()));
        plan.setMaxStudents(form.getMaxStudents());
        // Le tarif ne touche pas aux factures déjà émises : elles ont figé le leur.
        plan.setPrice(form.getPrice());
        if (Objects.nonNull(form.getActive())) plan.setActive(form.getActive());
        if (Objects.nonNull(form.getPosition())) plan.setPosition(form.getPosition());
        if (Objects.nonNull(form.getIsPublic())) plan.setPublic(form.getIsPublic());

        if (Objects.nonNull(form.getFeatured())) {
            if (form.getFeatured()) {
                this.feature(plan);
            } else {
                plan.setFeatured(false);
            }
        }
        // Retirée du site, elle ne peut plus y être mise en avant : la carte n'existerait pas.
        if (!plan.isPublic() || !plan.isActive()) plan.setFeatured(false);

        log.info("SUBSCRIPTION_PLAN_UPDATED: {} ({} FCFA)", plan.getCode(), plan.getPrice());
        return this.toDto(this.planRepository.saveAndFlush(plan));
    }

    /**
     * Suppression d'une formule.
     *
     * <p>Refusée dès qu'une école ou une facture s'y réfère : c'est le cas où l'on désactive. Une
     * formule jamais utilisée — créée par erreur, mal nommée — se supprime, elle ne laisse rien
     * derrière elle.
     */
    @Transactional
    public void delete(UUID id) {
        SubscriptionPlanEntity plan = this.planRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Formule d'abonnement introuvable."));

        long establishments = this.establishmentRepository.countBySubscriptionPlan(plan.getCode());
        long invoices = this.invoiceRepository.countByPlan(plan.getCode());
        if (establishments > 0 || invoices > 0) {
            throw new BadRequestException(String.format(
                    "%s est utilisée par %d école(s) et %d facture(s) : désactivez-la plutôt que "
                            + "de la supprimer, sinon leurs contrats désigneraient une formule disparue.",
                    plan.getLabel(), establishments, invoices));
        }

        this.planRepository.delete(plan);
        log.info("SUBSCRIPTION_PLAN_DELETED: {}", plan.getCode());
    }

    /**
     * Met une formule en avant, et retire la mise en avant des autres.
     *
     * <p>Tenu ici plutôt que par un index unique partiel : basculer d'une formule à l'autre dans la
     * même transaction violerait la contrainte avant la fin de l'écriture. Même raisonnement que
     * pour l'année scolaire active d'un établissement.
     */
    private void feature(SubscriptionPlanEntity plan) {
        this.planRepository.findByFeaturedTrue().stream()
                .filter(other -> !other.getId().equals(plan.getId()))
                .forEach(other -> {
                    other.setFeatured(false);
                    this.planRepository.save(other);
                });
        plan.setFeatured(true);
    }

    /* ---------------- Outillage ---------------- */

    private void checkPrice(BigDecimal price) {
        if (Objects.isNull(price) || price.signum() < 0 || price.compareTo(MAX_PRICE) > 0) {
            throw new BadRequestException(String.format(
                    "Le tarif doit être compris entre 0 et %s FCFA.", MAX_PRICE.toPlainString()));
        }
    }

    /**
     * Code dérivé du libellé : « Offre de lancement » donne {@code OFFRE_DE_LANCEMENT}.
     *
     * <p>Suffixé d'un rang en cas de collision plutôt que refusé : deux formules peuvent
     * légitimement porter le même nom à deux époques, et rien n'oblige YPYit à inventer un code.
     */
    private String allocateCode(String label) {
        String base = Normalizer.normalize(label.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_|_$", "");
        if (base.isBlank()) base = "FORMULE";
        if (base.length() > CODE_MAX_LENGTH) base = base.substring(0, CODE_MAX_LENGTH);

        String candidate = base;
        int rank = 2;
        while (this.planRepository.existsByCode(candidate)) {
            String suffix = "_" + rank++;
            candidate = base.length() + suffix.length() > CODE_MAX_LENGTH
                    ? base.substring(0, CODE_MAX_LENGTH - suffix.length()) + suffix
                    : base + suffix;
        }
        return candidate;
    }

    private int nextPosition() {
        return this.planRepository.findByOrderByPositionAscLabelAsc().stream()
                .mapToInt(SubscriptionPlanEntity::getPosition)
                .max().orElse(0) + 1;
    }

    private String trimmed(String value) {
        return Objects.isNull(value) || value.isBlank() ? null : value.trim();
    }

    private SubscriptionPlanDto toDto(SubscriptionPlanEntity plan) {
        return SubscriptionPlanDto.builder()
                .id(plan.getId())
                .plan(plan.getCode())
                .label(plan.getLabel())
                .description(plan.getDescription())
                .maxStudents(plan.getMaxStudents())
                .price(plan.getPrice())
                .active(plan.isActive())
                .position(plan.getPosition())
                .isPublic(plan.isPublic())
                .featured(plan.isFeatured())
                .establishmentCount(this.establishmentRepository.countBySubscriptionPlan(plan.getCode()))
                .invoiceCount(this.invoiceRepository.countByPlan(plan.getCode()))
                .build();
    }

    /**
     * Vue publique d'une formule.
     *
     * <p>Volontairement amputée : ni identifiant, ni nombre d'écoles, ni nombre de factures. Le
     * site n'en a pas besoin, et publier combien d'écoles ont souscrit à chaque palier reviendrait
     * à publier la structure du chiffre d'affaires de YPYit.
     */
    private SubscriptionPlanDto toPublicDto(SubscriptionPlanEntity plan) {
        return SubscriptionPlanDto.builder()
                .plan(plan.getCode())
                .label(plan.getLabel())
                .description(plan.getDescription())
                .maxStudents(plan.getMaxStudents())
                .price(plan.getPrice())
                .active(true)
                .position(plan.getPosition())
                .isPublic(true)
                .featured(plan.isFeatured())
                .build();
    }
}
