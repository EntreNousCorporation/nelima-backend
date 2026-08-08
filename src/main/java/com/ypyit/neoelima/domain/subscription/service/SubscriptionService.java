package com.ypyit.neoelima.domain.subscription.service;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QStudentEntity;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.subscription.dto.SubscriptionDueDto;
import com.ypyit.neoelima.domain.subscription.dto.SubscriptionInvoiceDto;
import com.ypyit.neoelima.domain.subscription.entity.SubscriptionInvoiceEntity;
import com.ypyit.neoelima.domain.subscription.entity.SubscriptionPlanEntity;
import com.ypyit.neoelima.domain.subscription.repository.SubscriptionInvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Abonnement des écoles à Nelima : formule, périodes et factures.
 *
 * <p>Trois principes tiennent tout le reste.
 *
 * <p><strong>La formule est stockée, l'effectif ne fait que la proposer.</strong> Enterprise se
 * négocie sur devis et une école peut obtenir un tarif consenti ; recalculer la formule à chaque
 * lecture reviendrait à modifier un contrat sans que personne ne l'ait décidé.
 *
 * <p><strong>Une facture est une pièce.</strong> Formule, montant et numéro y sont recopiés à
 * l'émission. Renégocier un tarif ne réécrit pas le chiffre d'affaires des mois passés.
 *
 * <p><strong>Rien ne s'émet tout seul.</strong> Le service dit quelles écoles sont à facturer ;
 * c'est YPYit qui émet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    /** Délai de règlement accordé à une école, à compter de l'émission. */
    private static final int PAYMENT_TERMS_DAYS = 30;

    private final SubscriptionInvoiceRepository invoiceRepository;
    private final SubscriptionInvoiceNumberAllocator numberAllocator;
    private final SubscriptionPlanService planService;
    private final EstablishmentRepository establishmentRepository;
    private final JPAQueryFactory queryFactory;

    /* ---------------- Formule ---------------- */

    /**
     * Fixe la formule d'une école et, au premier passage, sa date de souscription.
     *
     * <p>La date n'est posée qu'une fois : c'est elle qui ancre toutes les périodes de facturation,
     * et la déplacer déplacerait des périodes déjà facturées.
     */
    @Transactional
    public void subscribe(UUID establishmentId, String planCode, LocalDate subscribedAt) {
        // La formule doit exister : une école rattachée à un code inconnu ne serait jamais
        // facturable, et le défaut ne se verrait qu'au moment d'émettre.
        SubscriptionPlanEntity plan = this.planService.byCode(planCode);
        EstablishmentEntity establishment = this.establishmentRepository.findById(establishmentId)
                .orElseThrow(() -> new NotFoundException(String.format(
                        "Establishment with provided id %s not found", establishmentId)));

        establishment.setSubscriptionPlan(plan.getCode());
        if (Objects.isNull(establishment.getSubscribedAt())) {
            establishment.setSubscribedAt(
                    Objects.requireNonNullElseGet(subscribedAt, () -> LocalDate.now(ABIDJAN)));
        } else if (Objects.nonNull(subscribedAt)
                && !subscribedAt.equals(establishment.getSubscribedAt())
                && !this.invoiceRepository
                        .findByEstablishment_IdOrderByPeriodStartDesc(establishmentId).isEmpty()) {
            throw new BadRequestException(
                    "La date de souscription ne peut plus changer : des factures s'appuient dessus.");
        } else if (Objects.nonNull(subscribedAt)) {
            establishment.setSubscribedAt(subscribedAt);
        }

        this.establishmentRepository.saveAndFlush(establishment);
        log.info("SUBSCRIPTION_SET: établissement {} → {}", establishmentId, plan.getCode());
    }

    /* ---------------- Périodes et factures dues ---------------- */

    /**
     * Écoles dont une période est échue sans facture correspondante.
     *
     * <p>La période court sur douze mois à compter de la souscription, et non sur l'année scolaire :
     * cela évite la question du prorata pour une école qui signe en mars, qui est une décision
     * commerciale et non technique.
     */
    public List<SubscriptionDueDto> due() {
        LocalDate today = LocalDate.now(ABIDJAN);
        Map<UUID, Long> students = this.studentsByEstablishment();

        List<SubscriptionDueDto> due = new ArrayList<>();
        for (EstablishmentEntity establishment : this.establishmentRepository.findAll()) {
            if (!establishment.isPrimary()
                    || Objects.isNull(establishment.getSubscriptionPlan())
                    || Objects.isNull(establishment.getSubscribedAt())) {
                continue;
            }

            LocalDate periodStart = this.currentPeriodStart(establishment.getSubscribedAt(), today);
            boolean alreadyIssued = this.invoiceRepository
                    .findByEstablishment_IdAndPeriodStartAndCancelledFalse(establishment.getId(), periodStart)
                    .isPresent();
            if (alreadyIssued) {
                continue;
            }

            SubscriptionPlanEntity plan = this.planService.byCode(establishment.getSubscriptionPlan());
            long count = students.getOrDefault(establishment.getId(), 0L);
            String suggested = this.planService.suggestedFor(count);

            due.add(SubscriptionDueDto.builder()
                    .establishmentId(establishment.getId().toString())
                    .establishmentName(establishment.getName())
                    .plan(plan.getCode())
                    .planLabel(plan.getLabel())
                    .amount(plan.getPrice())
                    .periodStart(periodStart)
                    .periodEnd(periodStart.plusYears(1).minusDays(1))
                    .studentCount(count)
                    .suggestedPlan(Objects.equals(suggested, plan.getCode()) ? null : suggested)
                    .build());
        }

        due.sort(Comparator.comparing(SubscriptionDueDto::getPeriodStart));
        return due;
    }

    /**
     * Début de la période en cours pour une souscription donnée.
     *
     * <p>Anniversaire par anniversaire : une école ayant souscrit le 14 mars 2025 est facturable au
     * 14 mars 2025, puis au 14 mars 2026. Le 29 février se replie sur le 28 les années communes,
     * ce dont {@code plusYears} se charge.
     */
    public LocalDate currentPeriodStart(LocalDate subscribedAt, LocalDate today) {
        LocalDate start = subscribedAt;
        while (!start.plusYears(1).isAfter(today)) {
            start = start.plusYears(1);
        }
        return start;
    }

    /* ---------------- Émission et règlement ---------------- */

    /**
     * Émet la facture de la période en cours d'une école.
     *
     * <p>Le montant et la formule sont figés ici. L'unicité par période est doublée en base : émettre
     * deux fois la même année doublerait la dette de l'école sans que personne ne s'en aperçoive.
     */
    @Transactional
    public SubscriptionInvoiceDto issue(UUID establishmentId) {
        EstablishmentEntity establishment = this.establishmentRepository.findById(establishmentId)
                .orElseThrow(() -> new NotFoundException(String.format(
                        "Establishment with provided id %s not found", establishmentId)));

        String planCode = establishment.getSubscriptionPlan();
        if (Objects.isNull(planCode) || Objects.isNull(establishment.getSubscribedAt())) {
            throw new BadRequestException(
                    "Cette école n'a pas de formule d'abonnement : renseignez-la avant de facturer.");
        }

        SubscriptionPlanEntity plan = this.planService.byCode(planCode);
        LocalDate today = LocalDate.now(ABIDJAN);
        LocalDate periodStart = this.currentPeriodStart(establishment.getSubscribedAt(), today);
        this.invoiceRepository
                .findByEstablishment_IdAndPeriodStartAndCancelledFalse(establishmentId, periodStart)
                .ifPresent(existing -> {
                    throw new BadRequestException(String.format(
                            "La période du %s est déjà facturée (%s).", periodStart, existing.getNumber()));
                });

        SubscriptionInvoiceEntity invoice = SubscriptionInvoiceEntity.builder()
                .establishment(establishment)
                .number(this.numberAllocator.allocate(today.getYear()))
                .plan(plan.getCode())
                .planLabel(plan.getLabel())
                .amount(plan.getPrice())
                .periodStart(periodStart)
                .periodEnd(periodStart.plusYears(1).minusDays(1))
                .issuedAt(Instant.now())
                .dueAt(today.plusDays(PAYMENT_TERMS_DAYS))
                .cancelled(false)
                .build();

        SubscriptionInvoiceEntity saved = this.invoiceRepository.saveAndFlush(invoice);
        log.info("SUBSCRIPTION_INVOICE_ISSUED: {} à {} pour {}",
                saved.getNumber(), establishment.getName(), periodStart);
        return toDto(saved, today);
    }

    /** Constate un règlement reçu hors plateforme : virement, espèces, mobile money. */
    @Transactional
    public SubscriptionInvoiceDto recordPayment(UUID invoiceId, LocalDate paidOn,
                                                String method, String reference) {
        SubscriptionInvoiceEntity invoice = this.load(invoiceId);
        if (invoice.isCancelled()) {
            throw new BadRequestException("Cette facture est annulée : elle ne peut pas être réglée.");
        }
        if (invoice.isPaid()) {
            throw new BadRequestException(String.format(
                    "La facture %s est déjà réglée.", invoice.getNumber()));
        }

        LocalDate day = Objects.requireNonNullElseGet(paidOn, () -> LocalDate.now(ABIDJAN));
        if (day.isBefore(invoice.getPeriodStart())) {
            // Un règlement antérieur à la période facturée signale une saisie de date fautive, et
            // fausserait le chiffre d'affaires du mois où on le compterait.
            throw new BadRequestException(
                    "La date de règlement est antérieure au début de la période facturée.");
        }

        invoice.setPaidAt(day.atStartOfDay(ABIDJAN).toInstant());
        invoice.setPaymentMethod(method);
        invoice.setPaymentReference(reference);
        this.invoiceRepository.saveAndFlush(invoice);

        log.info("SUBSCRIPTION_INVOICE_PAID: {} le {} par {}", invoice.getNumber(), day, method);
        return toDto(invoice, LocalDate.now(ABIDJAN));
    }

    /**
     * Annule une facture émise par erreur.
     *
     * <p>Annulée, jamais supprimée : retirer une ligne ferait un trou dans la suite comptable,
     * qu'aucun contrôle ne saurait ensuite expliquer.
     */
    @Transactional
    public SubscriptionInvoiceDto cancel(UUID invoiceId, String reason) {
        SubscriptionInvoiceEntity invoice = this.load(invoiceId);
        if (invoice.isPaid()) {
            throw new BadRequestException(
                    "Une facture réglée ne s'annule pas : le remboursement se traite hors outil.");
        }
        invoice.setCancelled(true);
        invoice.setCancellationReason(reason);
        this.invoiceRepository.saveAndFlush(invoice);

        log.info("SUBSCRIPTION_INVOICE_CANCELLED: {} — {}", invoice.getNumber(), reason);
        return toDto(invoice, LocalDate.now(ABIDJAN));
    }

    /* ---------------- Lectures ---------------- */

    public List<SubscriptionInvoiceDto> invoices() {
        LocalDate today = LocalDate.now(ABIDJAN);
        return this.invoiceRepository.findByOrderByIssuedAtDesc().stream()
                .map(invoice -> toDto(invoice, today))
                .toList();
    }

    public List<SubscriptionInvoiceDto> invoicesOf(UUID establishmentId) {
        LocalDate today = LocalDate.now(ABIDJAN);
        return this.invoiceRepository
                .findByEstablishment_IdOrderByPeriodStartDesc(establishmentId).stream()
                .map(invoice -> toDto(invoice, today))
                .toList();
    }

    static SubscriptionInvoiceDto toDto(SubscriptionInvoiceEntity invoice, LocalDate today) {
        return SubscriptionInvoiceDto.builder()
                .id(invoice.getId().toString())
                .number(invoice.getNumber())
                .establishmentId(invoice.getEstablishment().getId().toString())
                .establishmentName(invoice.getEstablishment().getName())
                .plan(invoice.getPlan())
                // Le libellé vient de la facture, jamais de la grille : c'est ce qui la rend
                // relisible dix ans plus tard, même si la formule a changé de nom entre-temps.
                .planLabel(invoice.getPlanLabel())
                .amount(invoice.getAmount())
                .periodStart(invoice.getPeriodStart())
                .periodEnd(invoice.getPeriodEnd())
                .issuedAt(invoice.getIssuedAt())
                .dueAt(invoice.getDueAt())
                .paidAt(invoice.getPaidAt())
                .paymentMethod(invoice.getPaymentMethod())
                .paymentReference(invoice.getPaymentReference())
                .cancelled(invoice.isCancelled())
                .cancellationReason(invoice.getCancellationReason())
                .status(invoice.isCancelled() ? "CANCELLED"
                        : invoice.isPaid() ? "PAID"
                                : invoice.isLate(today) ? "LATE" : "ISSUED")
                .build();
    }

    private SubscriptionInvoiceEntity load(UUID invoiceId) {
        return this.invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new NotFoundException(String.format(
                        "Subscription invoice with provided id %s not found", invoiceId)));
    }

    private Map<UUID, Long> studentsByEstablishment() {
        QStudentEntity student = QStudentEntity.studentEntity;
        Map<UUID, Long> counts = new HashMap<>();
        this.queryFactory
                .select(student.establishment.id, student.count())
                .from(student)
                .groupBy(student.establishment.id)
                .fetch()
                .forEach(row -> counts.put(row.get(student.establishment.id),
                        Objects.requireNonNullElse(row.get(student.count()), 0L)));
        return counts;
    }

    /** Tarif courant d'une formule, pour les appelants qui composent leurs propres agrégats. */
    public BigDecimal priceOf(String planCode) {
        return this.planService.byCode(planCode).getPrice();
    }
}
