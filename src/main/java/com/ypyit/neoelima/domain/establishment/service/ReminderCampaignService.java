package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.common.utils.XofFormat;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.ReminderCampaignDto;
import com.ypyit.neoelima.domain.establishment.dto.ReminderTargetDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.ReminderCampaignEntity;
import com.ypyit.neoelima.domain.establishment.entity.ReminderDeliveryEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.AuditAction;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.enums.NotificationEvent;
import com.ypyit.neoelima.domain.establishment.enums.ReminderChannel;
import com.ypyit.neoelima.domain.establishment.enums.ReminderOrigin;
import com.ypyit.neoelima.domain.establishment.enums.ReminderTarget;
import com.ypyit.neoelima.domain.establishment.form.ReminderCampaignForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.ReminderCampaignRepository;
import com.ypyit.neoelima.domain.establishment.repository.ReminderDeliveryRepository;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Campagnes de relance des familles en retard.
 *
 * <p>Deux garde-fous, et aucun n'est décoratif. Le décompte se consulte <strong>avant</strong>
 * l'envoi : un SMS se facture, et une école doit savoir combien de familles elle s'apprête à
 * joindre. Et l'envoi passe par {@link ReminderDispatcher}, qui écarte les destinataires déjà
 * relancés le jour même — par une autre campagne ou par le rappel automatique.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReminderCampaignService {

    /** Fenêtre au-delà de laquelle un règlement n'est plus attribué à la campagne. */
    private static final int ATTRIBUTION_DAYS = 7;

    private final InstallmentRepository installmentRepository;
    private final ReminderCampaignRepository campaignRepository;
    private final ReminderDeliveryRepository deliveryRepository;
    private final EstablishmentRepository establishmentRepository;
    private final ReminderDispatcher dispatcher;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;

    /**
     * L'événement auquel une cible se rapporte, pour interroger le bon réglage.
     *
     * <p>« Toutes les tranches dues » est traitée comme un retard : elle contient les échues, et
     * c'est le réglage le plus proche de l'intention d'une école qui relance largement.
     */
    private static NotificationEvent eventOf(ReminderTarget target) {
        return ReminderTarget.DUE_SOON.equals(target)
                ? NotificationEvent.INSTALLMENT_DUE_SOON
                : NotificationEvent.INSTALLMENT_OVERDUE;
    }

    /** Ce que chaque cible représente aujourd'hui : familles jointes, montant en jeu. */
    public List<ReminderTargetDto> targets() {
        UUID scope = this.scope();
        List<InstallmentEntity> unpaid = this.unpaidOf(scope);

        return java.util.Arrays.stream(ReminderTarget.values())
                .map(target -> this.describe(target, matching(unpaid, target)))
                .toList();
    }

    public List<ReminderCampaignDto> findAll() {
        return this.campaignRepository.findByEstablishment_IdOrderBySentAtDesc(this.scope()).stream()
                .map(this::toDto).toList();
    }

    /**
     * Crée la campagne et envoie les rappels.
     *
     * <p>La campagne est enregistrée même si rien n'est parti : une école qui relance dans le vide
     * doit le voir, et un historique qui ne garde que les succès ferait croire que le canal
     * fonctionne.
     */
    @Transactional
    public ReminderCampaignDto send(ReminderCampaignForm form) {
        UUID scope = this.scope();
        EstablishmentEntity establishment = this.establishmentRepository.findById(scope)
                .orElseThrow(() -> new NotFoundException("Establishment not found"));

        List<InstallmentEntity> targeted = matching(this.unpaidOf(scope), form.getTarget());
        if (targeted.isEmpty()) {
            throw new BadRequestException(
                    "Aucune famille ne correspond à cette cible : rien ne serait envoyé.");
        }

        NotificationEvent event = eventOf(form.getTarget());
        // Le refus est explicite plutôt que silencieux : une campagne enregistrée à zéro envoi
        // laisserait l'école chercher la panne du côté du canal, pas de son propre réglage.
        if (this.dispatcher.allowedChannels(targeted.getFirst().getStudentFee().getStudent(),
                event, form.getChannels()).isEmpty()) {
            throw new BadRequestException(
                    "Les canaux choisis sont désactivés dans les paramètres de notification.");
        }

        ReminderCampaignEntity campaign = this.campaignRepository.saveAndFlush(
                ReminderCampaignEntity.builder()
                        .name(form.getName().trim())
                        .target(form.getTarget())
                        .channels(new HashSet<>(form.getChannels()))
                        .messageTemplate(form.getMessageTemplate().trim())
                        .sentAt(java.time.Instant.now())
                        .establishment(establishment)
                        .build());

        ReminderDispatcher.Result total = new ReminderDispatcher.Result(0, 0);
        for (InstallmentEntity installment : targeted) {
            total = total.plus(this.dispatcher.remind(installment,
                    form.getChannels(), ReminderOrigin.CAMPAIGN, campaign, event,
                    "Échéance à régler", this.render(form.getMessageTemplate(), installment)));
        }

        campaign.setSentCount(total.sent());
        campaign.setSkippedCount(total.skipped());
        this.campaignRepository.saveAndFlush(campaign);

        // Solliciter des familles se journalise : c'est un geste visible de l'extérieur, et il se
        // paie quand le canal est le SMS.
        this.auditService.record(scope, AuditAction.REMINDER_CAMPAIGN_SENT, campaign.getName(),
                String.format("Cible %s, %d envoi(s), %d écarté(s), canaux %s",
                        form.getTarget(), total.sent(), total.skipped(), form.getChannels()));

        log.info("REMINDER_CAMPAIGN_SENT: {} — {} envoi(s), {} écarté(s)",
                campaign.getId(), total.sent(), total.skipped());
        return this.toDto(campaign);
    }

    /**
     * Remplace les variables du gabarit pour une famille.
     *
     * <p>Une variable inconnue est laissée telle quelle plutôt que vidée : un message où il manque
     * un mot se remarque et se corrige, un message où le montant a disparu part sans qu'on le voie.
     */
    String render(String template, InstallmentEntity installment) {
        StudentEntity student = installment.getStudentFee().getStudent();
        SchoolClassEntity schoolClass = student.getSchoolClass();
        long late = Objects.isNull(installment.getDueDate()) ? 0
                : Math.max(0, ChronoUnit.DAYS.between(installment.getDueDate(), LocalDate.now()));

        return template
                .replace("{parent}", firstGuardianName(student))
                .replace("{eleve}", String.format("%s %s", student.getLastName(), student.getFirstName()))
                .replace("{classe}", Objects.isNull(schoolClass) ? "sa classe" : schoolClass.getName())
                .replace("{montant}", XofFormat.format(installment.getAmount()))
                .replace("{retard}", String.valueOf(late));
    }

    private ReminderTargetDto describe(ReminderTarget target, List<InstallmentEntity> installments) {
        Set<UUID> families = new HashSet<>();
        Set<UUID> recipients = new HashSet<>();
        for (InstallmentEntity installment : installments) {
            StudentEntity student = installment.getStudentFee().getStudent();
            families.add(student.getId());
            student.getParentUsers().stream()
                    .map(UserEntity::getId).filter(Objects::nonNull).forEach(recipients::add);
        }

        return ReminderTargetDto.builder()
                .target(target)
                .label(labelOf(target))
                .installmentCount(installments.size())
                .familyCount(families.size())
                // Ce sont les tuteurs qui reçoivent, pas les élèves : c'est ce nombre qui approche
                // le coût d'un envoi facturé.
                .recipientCount(recipients.size())
                .amountDue(installments.stream()
                        .map(InstallmentEntity::getAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .build();
    }

    private ReminderCampaignDto toDto(ReminderCampaignEntity campaign) {
        List<ReminderDeliveryEntity> deliveries = this.deliveryRepository.findByCampaign_Id(campaign.getId());

        // Une tranche visée, réglée dans les jours qui ont suivi, est portée au crédit de la
        // campagne. Au-delà, le lien devient une coïncidence.
        Set<UUID> counted = new HashSet<>();
        long paid = 0;
        BigDecimal recovered = BigDecimal.ZERO;
        for (ReminderDeliveryEntity delivery : deliveries) {
            InstallmentEntity installment = delivery.getInstallment();
            if (!counted.add(installment.getId())) {
                continue;
            }
            if (!InstallmentStatus.PAID.equals(installment.getStatus())
                    || Objects.isNull(installment.getPaidAt())) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(campaign.getSentAt(), installment.getPaidAt());
            if (days >= 0 && days <= ATTRIBUTION_DAYS) {
                paid++;
                recovered = recovered.add(Objects.requireNonNullElse(
                        installment.getAmount(), BigDecimal.ZERO));
            }
        }

        return ReminderCampaignDto.builder()
                .id(campaign.getId().toString())
                .name(campaign.getName())
                .target(campaign.getTarget())
                .targetLabel(labelOf(campaign.getTarget()))
                .channels(List.copyOf(campaign.getChannels()))
                .messageTemplate(campaign.getMessageTemplate())
                .sentAt(campaign.getSentAt())
                .sentCount(campaign.getSentCount())
                .skippedCount(campaign.getSkippedCount())
                .paidCount(paid)
                .recoveredAmount(recovered)
                .build();
    }

    private List<InstallmentEntity> unpaidOf(UUID establishmentId) {
        return this.installmentRepository
                .findByStudentFee_Student_Establishment_IdAndStatusAndDueDateNotNull(
                        establishmentId, InstallmentStatus.PENDING);
    }

    /** Filtre les tranches encore dues selon l'ancienneté du retard, ou l'imminence de l'échéance. */
    static List<InstallmentEntity> matching(List<InstallmentEntity> unpaid, ReminderTarget target) {
        LocalDate today = LocalDate.now();
        return unpaid.stream().filter(installment -> {
            long overdue = ChronoUnit.DAYS.between(installment.getDueDate(), today);
            return switch (target) {
                case LATE_30 -> overdue > 30;
                case LATE_7 -> overdue > 7;
                case DUE_SOON -> overdue <= 0 && overdue >= -5;
                case ALL_UNPAID -> true;
            };
        }).toList();
    }

    private static String labelOf(ReminderTarget target) {
        return switch (target) {
            case LATE_30 -> "Retard de plus de 30 jours";
            case LATE_7 -> "Retard de plus de 7 jours";
            case DUE_SOON -> "Échéance dans les 5 jours";
            case ALL_UNPAID -> "Toutes les tranches dues";
        };
    }

    private static String firstGuardianName(StudentEntity student) {
        return student.getParentUsers().stream().findFirst()
                .map(guardian -> String.format("%s %s",
                        Objects.toString(guardian.getLastName(), ""),
                        Objects.toString(guardian.getFirstName(), "")).trim())
                .filter(name -> !name.isEmpty())
                .orElse("Madame, Monsieur");
    }


    private UUID scope() {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        return scope;
    }
}
