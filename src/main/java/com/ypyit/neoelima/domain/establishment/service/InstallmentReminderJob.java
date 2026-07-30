package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.service.push.PushNotificationService;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Rappelle aux familles les tranches qui arrivent à échéance.
 *
 * <p>Sans rappel, une notification push n'annoncerait que des paiements déjà faits : l'intérêt du
 * canal est précisément de prévenir <em>avant</em> l'échéance, quand le parent peut encore agir.
 *
 * <p>Les échéances visées sont des dates <strong>exactes</strong> — aujourd'hui plus un décalage —
 * et non un intervalle. Un intervalle rappellerait la même tranche tous les jours jusqu'à son terme,
 * ce qu'un parent lit comme du harcèlement et finit par désactiver.
 *
 * <p>Ce travail ne conserve pas trace de ce qu'il a envoyé : deux exécutions le même jour
 * enverraient deux fois le même rappel. C'est assumé pour un rappel — le pire est un doublon,
 * jamais une erreur comptable — et c'est pourquoi la cadence est journalière et à heure fixe. Un
 * suivi par tranche deviendra nécessaire le jour où le rappel partira aussi par SMS, facturé à
 * l'envoi.
 */
@Slf4j
@Service
public class InstallmentReminderJob {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);

    private final InstallmentRepository installmentRepository;
    private final PushNotificationService pushNotificationService;

    /** Décalages, en jours, auxquels un rappel est envoyé avant l'échéance. */
    private final List<Integer> offsets;

    public InstallmentReminderJob(InstallmentRepository installmentRepository,
                                 PushNotificationService pushNotificationService,
                                 @Value("${nelima.billing.reminder-days-before:7,1}") List<Integer> offsets) {
        this.installmentRepository = installmentRepository;
        this.pushNotificationService = pushNotificationService;
        this.offsets = offsets;
    }

    @Scheduled(cron = "${nelima.billing.reminder-cron:0 0 8 * * *}", zone = "Africa/Abidjan")
    @Transactional(readOnly = true)
    public void remindUpcomingInstallments() {
        LocalDate today = LocalDate.now();
        int notified = 0;

        for (Integer offset : this.offsets) {
            LocalDate dueDate = today.plusDays(offset);
            List<InstallmentEntity> due = this.installmentRepository
                    .findByStatusAndDueDate(InstallmentStatus.PENDING, dueDate);

            for (InstallmentEntity installment : due) {
                notified += this.remind(installment, offset) ? 1 : 0;
            }
        }
        log.info("INSTALLMENT_REMINDERS_SENT: {} rappel(s) émis le {}", notified, today);
    }

    private boolean remind(InstallmentEntity installment, int daysBefore) {
        StudentEntity student = installment.getStudentFee().getStudent();
        List<UUID> guardians = student.getParentUsers().stream()
                .map(UserEntity::getId)
                .filter(Objects::nonNull)
                .toList();

        if (guardians.isEmpty()) {
            // Sans tuteur rattaché, personne à prévenir. L'école le voit dans ses impayés.
            log.debug("INSTALLMENT_REMINDER_SKIPPED: élève {} sans tuteur rattaché",
                    student.getRegistrationNumber());
            return false;
        }

        this.pushNotificationService.send(
                guardians,
                daysBefore <= 1 ? "Échéance demain" : "Échéance dans " + daysBefore + " jours",
                String.format("%s à régler pour l'élève %s avant le %s.",
                        formatXof(installment.getAmount()),
                        Objects.toString(student.getRegistrationNumber(), "—"),
                        installment.getDueDate().format(DAY)),
                Map.of("type", "INSTALLMENT_DUE",
                        "installmentId", installment.getId().toString(),
                        "studentId", student.getId().toString()));
        return true;
    }

    private static String formatXof(BigDecimal amount) {
        if (Objects.isNull(amount)) {
            return "0 FCFA";
        }
        return String.format("%,d FCFA", amount.setScale(0, RoundingMode.HALF_UP).longValue())
                .replace(',', ' ');
    }
}
