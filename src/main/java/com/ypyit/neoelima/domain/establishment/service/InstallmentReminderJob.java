package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.utils.XofFormat;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.enums.NotificationEvent;
import com.ypyit.neoelima.domain.establishment.enums.ReminderChannel;
import com.ypyit.neoelima.domain.establishment.enums.ReminderOrigin;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
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
 * <p>L'envoi passe par {@link ReminderDispatcher}, qui tient le registre des rappels déjà partis.
 * Ce travail est donc idempotent : deux exécutions le même jour n'envoient qu'une fois, ce qui
 * n'était pas vrai tant qu'il envoyait lui-même. Le registre est partagé avec les campagnes
 * lancées depuis le portail — c'est ce qui empêche une famille d'être relancée deux fois le même
 * jour par deux chemins différents, et facturée deux fois quand le canal est le SMS.
 */
@Slf4j
@Service
public class InstallmentReminderJob {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);

    private final InstallmentRepository installmentRepository;
    private final ReminderDispatcher reminderDispatcher;

    /** Décalages, en jours, auxquels un rappel est envoyé avant l'échéance. */
    private final List<Integer> offsets;

    public InstallmentReminderJob(InstallmentRepository installmentRepository,
                                 ReminderDispatcher reminderDispatcher,
                                 @Value("${nelima.billing.reminder-days-before:7,1}") List<Integer> offsets) {
        this.installmentRepository = installmentRepository;
        this.reminderDispatcher = reminderDispatcher;
        this.offsets = offsets;
    }

    @Scheduled(cron = "${nelima.billing.reminder-cron:0 0 8 * * *}", zone = "Africa/Abidjan")
    @Transactional
    public void remindUpcomingInstallments() {
        LocalDate today = LocalDate.now();
        int notified = 0;
        int skipped = 0;

        for (Integer offset : this.offsets) {
            LocalDate dueDate = today.plusDays(offset);
            List<InstallmentEntity> due = this.installmentRepository
                    .findByStatusAndDueDate(InstallmentStatus.PENDING, dueDate);

            for (InstallmentEntity installment : due) {
                ReminderDispatcher.Result result = this.remind(installment, offset);
                notified += result.sent();
                skipped += result.skipped();
            }
        }
        log.info("INSTALLMENT_REMINDERS_SENT: {} rappel(s) émis, {} déjà envoyé(s) le {}",
                notified, skipped, today);
    }

    private ReminderDispatcher.Result remind(InstallmentEntity installment, int daysBefore) {
        StudentEntity student = installment.getStudentFee().getStudent();
        return this.reminderDispatcher.remind(installment,
                // Les deux canaux sont proposés, et c'est le réglage de l'école qui tranche : le
                // SMS est fermé par défaut, parce que basculer un envoi quotidien sur un canal
                // facturé se décide. Ici, l'école l'a décidé aux paramètres.
                List.of(ReminderChannel.PUSH, ReminderChannel.SMS),
                ReminderOrigin.AUTOMATIC, null, NotificationEvent.INSTALLMENT_DUE_SOON,
                daysBefore <= 1 ? "Échéance demain" : "Échéance dans " + daysBefore + " jours",
                String.format("%s à régler pour l'élève %s avant le %s.",
                        XofFormat.format(installment.getAmount()),
                        Objects.toString(student.getRegistrationNumber(), "—"),
                        installment.getDueDate().format(DAY)));
    }

}
