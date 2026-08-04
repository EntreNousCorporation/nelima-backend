package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.service.push.PushNotificationService;
import com.ypyit.neoelima.common.service.sms.service.SmsSender;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.ReminderCampaignEntity;
import com.ypyit.neoelima.domain.establishment.entity.ReminderDeliveryEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.ReminderChannel;
import com.ypyit.neoelima.domain.establishment.enums.ReminderOrigin;
import com.ypyit.neoelima.domain.establishment.repository.ReminderDeliveryRepository;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Point de passage unique de tout rappel envoyé à une famille.
 *
 * <p>Le rappel automatique et les campagnes passent par ici, et c'est ce qui rend la règle
 * applicable : une famille déjà relancée aujourd'hui, sur ce canal, pour cette tranche, ne l'est
 * pas une seconde fois. Deux émetteurs indépendants ne pourraient pas la tenir — chacun ignorerait
 * ce que l'autre vient d'envoyer.
 *
 * <p>Un envoi n'est jamais critique : une erreur d'un service tiers est journalisée et n'interrompt
 * ni la campagne en cours, ni le travail programmé.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderDispatcher {

    private final ReminderDeliveryRepository deliveryRepository;
    private final PushNotificationService pushNotificationService;
    private final SmsSender smsSender;

    /** Ce qu'un envoi a produit : ce qui est parti, et ce qu'on a volontairement écarté. */
    public record Result(int sent, int skipped) {
        Result plus(Result other) {
            return new Result(this.sent + other.sent, this.skipped + other.skipped);
        }

        static Result none() {
            return new Result(0, 0);
        }
    }

    /**
     * Relance les tuteurs d'un élève pour une tranche.
     *
     * @param campaign campagne à l'origine de l'envoi, nulle pour le rappel automatique
     */
    @Transactional
    public Result remind(InstallmentEntity installment, Collection<ReminderChannel> channels,
                         ReminderOrigin origin, ReminderCampaignEntity campaign,
                         String title, String message) {
        StudentEntity student = installment.getStudentFee().getStudent();
        List<UserEntity> guardians = student.getParentUsers().stream()
                .filter(guardian -> Objects.nonNull(guardian.getId()))
                .toList();

        if (guardians.isEmpty()) {
            // Sans tuteur rattaché, personne à prévenir. L'école le voit dans ses impayés.
            log.debug("REMINDER_SKIPPED: élève {} sans tuteur rattaché",
                    student.getRegistrationNumber());
            return Result.none();
        }

        Result total = Result.none();
        for (UserEntity guardian : guardians) {
            for (ReminderChannel channel : channels) {
                total = total.plus(this.remindOne(installment, guardian, channel, origin, campaign,
                        title, message));
            }
        }
        return total;
    }

    private Result remindOne(InstallmentEntity installment, UserEntity guardian,
                             ReminderChannel channel, ReminderOrigin origin,
                             ReminderCampaignEntity campaign, String title, String message) {
        LocalDate today = LocalDate.now();

        if (this.deliveryRepository.existsByInstallment_IdAndRecipient_IdAndChannelAndSentOn(
                installment.getId(), guardian.getId(), channel, today)) {
            return new Result(0, 1);
        }

        boolean delivered = switch (channel) {
            case PUSH -> this.sendPush(installment, guardian, title, message);
            case SMS -> this.sendSms(guardian, message);
        };

        if (!delivered) {
            // Rien n'est écrit au registre : on n'a pas relancé, et une nouvelle tentative doit
            // rester possible. Inscrire un envoi qui n'a pas eu lieu masquerait la famille.
            return Result.none();
        }

        this.deliveryRepository.save(ReminderDeliveryEntity.builder()
                .installment(installment)
                .recipient(guardian)
                .channel(channel)
                .origin(origin)
                .campaign(campaign)
                .sentOn(today)
                .sentAt(Instant.now())
                .build());
        return new Result(1, 0);
    }

    private boolean sendPush(InstallmentEntity installment, UserEntity guardian,
                             String title, String message) {
        try {
            this.pushNotificationService.send(List.of(guardian.getId()), title, message,
                    Map.of("type", "INSTALLMENT_DUE",
                            "installmentId", installment.getId().toString()));
            return true;
        } catch (RuntimeException e) {
            log.error("REMINDER_PUSH_FAILED: tuteur {}", guardian.getId(), e);
            return false;
        }
    }

    private boolean sendSms(UserEntity guardian, String message) {
        Optional<String> phone = phoneOf(guardian);
        if (phone.isEmpty()) {
            // Un tuteur sans numéro n'est pas un échec : il a pu être joint par notification.
            log.debug("REMINDER_SMS_SKIPPED: tuteur {} sans numéro", guardian.getId());
            return false;
        }
        try {
            this.smsSender.send(message, phone.get());
            return true;
        } catch (Exception e) {
            log.error("REMINDER_SMS_FAILED: tuteur {}", guardian.getId(), e);
            return false;
        }
    }

    /** Numéro principal du tuteur, à défaut le premier connu. */
    private static Optional<String> phoneOf(UserEntity guardian) {
        List<ContactEntity> phones = guardian.getContacts().stream()
                .filter(contact -> ContactType.PHONE_NUMBER.equals(contact.getType()))
                .filter(contact -> Objects.nonNull(contact.getValue()))
                .toList();
        return phones.stream().filter(ContactEntity::isPrimary).findFirst()
                .or(() -> phones.stream().findFirst())
                .map(ContactEntity::getValue);
    }
}
