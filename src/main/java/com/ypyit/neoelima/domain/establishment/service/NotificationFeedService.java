package com.ypyit.neoelima.domain.establishment.service;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.utils.XofFormat;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.NotificationDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QActivityEnrollmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QActivityEntity;
import com.ypyit.neoelima.domain.establishment.entity.QInstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QSchoolEventEntity;
import com.ypyit.neoelima.domain.establishment.entity.QStudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QStudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolEventEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.EnrollmentSource;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.enums.NotificationKind;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.payment.entity.QPaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.entity.QReceiptEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fil de notifications, pour l'école comme pour la famille.
 *
 * <p>Rien n'y est stocké. Chaque élément est <strong>déduit d'un fait daté</strong> qui existe
 * déjà : une inscription venue d'une famille, un encaissement sans reçu, une échéance arrivée à
 * terme, un reçu émis, un événement publié. Une table de notifications n'aurait porté qu'un doublon
 * de ces faits, qu'il aurait fallu tenir d'accord avec eux — et qui aurait divergé au premier oubli
 * d'écriture.
 *
 * <p>L'état « lu » tient donc en une seule date, posée sur le compte : un élément postérieur est
 * non lu. Ouvrir le panneau la déplace. La même colonne sert les deux publics, puisqu'un
 * utilisateur n'est jamais des deux à la fois.
 *
 * <p>Peu de natures, et chacune appelle un geste. Une cloche qui annonce ce sur quoi on ne peut
 * rien se vide de son sens en une semaine.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationFeedService {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    /** Fenêtre du fil. Au-delà, un fait n'appelle plus une notification mais un écran de travail. */
    private static final int WINDOW_DAYS = 30;

    /** Plafond du fil : passé cette longueur, on ne lit plus, on subit. */
    private static final int MAX_ITEMS = 30;

    /**
     * Décalages des rappels d'échéance, du plus lointain au plus proche.
     *
     * <p>Ceux de {@code InstallmentReminderJob} — le fil doit dire exactement ce que le téléphone a
     * déjà dit. Recopiés plutôt que lus dans la configuration : les faire diverger n'est pas une
     * panne mais un décalage silencieux, et le premier élément sert aussi de borne de recherche.
     */
    private static final List<Integer> REMINDER_OFFSETS = List.of(7, 1);

    /** Heure du rappel automatique, à Abidjan. */
    private static final int REMINDER_HOUR = 8;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRENCH);

    private final JPAQueryFactory queryFactory;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final CurrentUserProvider currentUserProvider;

    public List<NotificationDto> feed() {
        UserEntity user = this.currentUserProvider.currentUser();
        Instant seenAt = user.getNotificationsSeenAt();
        Instant since = Instant.now().minus(WINDOW_DAYS, ChronoUnit.DAYS);

        // Le public se lit sur l'utilisateur, jamais sur un paramètre. Dériver une portée
        // établissement pour tout le monde refuserait au parent l'accès à ses propres données —
        // c'est déjà arrivé une fois, par cet exact chemin.
        List<NotificationDto> items = this.currentUserProvider.hasEstablishmentScope()
                ? this.schoolItems(since)
                : this.familyItems(user.getId(), since);

        return items.stream()
                .sorted(Comparator.comparing(NotificationDto::getOccurredAt).reversed())
                .limit(MAX_ITEMS)
                .peek(item -> item.setUnread(
                        Objects.isNull(seenAt) || item.getOccurredAt().isAfter(seenAt)))
                .toList();
    }

    /**
     * Marque le fil comme lu.
     *
     * <p>L'horodatage est celui de l'appel, non celui du dernier élément affiché : un fait survenu
     * pendant la lecture doit rester non lu, sinon il disparaît sans avoir été vu.
     */
    @Transactional
    public void markSeen() {
        UserEntity user = this.currentUserProvider.currentUser();
        user.setNotificationsSeenAt(Instant.now());
        this.userRepository.saveAndFlush(user);
    }

    /** Ce que l'école a à traiter, filtré permission par permission. */
    private List<NotificationDto> schoolItems(Instant since) {
        UUID scope = this.scope();
        List<NotificationDto> items = new ArrayList<>();
        if (this.currentUserProvider.hasPermission("activity:read")) {
            items.addAll(this.familyEnrolments(scope, since));
        }
        if (this.currentUserProvider.hasPermission("accounting:read")) {
            items.addAll(this.paymentsToReconcile(scope, since));
        }
        if (this.currentUserProvider.hasPermission("fee:read")) {
            items.addAll(this.overdueInstalments(scope, since));
        }
        return items;
    }

    /** Inscriptions saisies par les familles depuis l'application : elles attendent une place. */
    private List<NotificationDto> familyEnrolments(UUID scope, Instant since) {
        QActivityEnrollmentEntity enrolment = QActivityEnrollmentEntity.activityEnrollmentEntity;
        QActivityEntity activity = QActivityEntity.activityEntity;
        QStudentEntity student = QStudentEntity.studentEntity;

        return this.queryFactory
                .select(enrolment.id, enrolment.requestedAt, enrolment.status,
                        activity.name, student.firstName, student.lastName)
                .from(enrolment)
                .join(enrolment.activity, activity)
                .join(enrolment.student, student)
                .where(activity.establishment.id.eq(scope),
                        enrolment.source.eq(EnrollmentSource.PARENT),
                        enrolment.requestedAt.after(since))
                .orderBy(enrolment.requestedAt.desc())
                .limit(MAX_ITEMS)
                .fetch().stream()
                .map(row -> NotificationDto.builder()
                        .id("activity@" + row.get(enrolment.id))
                        .kind(NotificationKind.ACTIVITY_REQUEST)
                        .title("Inscription depuis l'application")
                        .detail(String.format("%s %s · %s",
                                Objects.toString(row.get(student.lastName), ""),
                                Objects.toString(row.get(student.firstName), ""),
                                Objects.toString(row.get(activity.name), "")).trim())
                        .occurredAt(row.get(enrolment.requestedAt))
                        .link("/app/activites")
                        .build())
                .toList();
    }

    /**
     * Encaissements confirmés sans reçu émis.
     *
     * <p>C'est une pièce comptable manquante, pas une statistique : elle se traite avant la clôture,
     * et personne ne la découvre en parcourant une liste de huit mille lignes.
     */
    private List<NotificationDto> paymentsToReconcile(UUID scope, Instant since) {
        QPaymentIntentEntity intent = QPaymentIntentEntity.paymentIntentEntity;
        QReceiptEntity receipt = QReceiptEntity.receiptEntity;
        QInstallmentEntity installment = QInstallmentEntity.installmentEntity;
        QStudentFeeEntity studentFee = QStudentFeeEntity.studentFeeEntity;
        QStudentEntity student = QStudentEntity.studentEntity;

        return this.queryFactory
                .select(intent.id, intent.settledAt, intent.amountSchool,
                        student.firstName, student.lastName)
                .from(intent)
                .join(intent.installment, installment)
                .join(installment.studentFee, studentFee)
                .join(studentFee.student, student)
                .where(student.establishment.id.eq(scope),
                        intent.status.eq(PaymentIntentStatus.SUCCEEDED),
                        intent.settledAt.after(since),
                        // Sous-requête plutôt que jointure externe : un reçu par tentative, et une
                        // jointure dupliquerait la ligne le jour où il y en aurait deux.
                        intent.id.notIn(com.querydsl.jpa.JPAExpressions
                                .select(receipt.paymentIntent.id).from(receipt)
                                .where(receipt.paymentIntent.id.isNotNull())))
                .orderBy(intent.settledAt.desc())
                .limit(MAX_ITEMS)
                .fetch().stream()
                .map(row -> NotificationDto.builder()
                        .id("reconcile@" + row.get(intent.id))
                        .kind(NotificationKind.PAYMENT_TO_RECONCILE)
                        .title("Encaissement sans reçu")
                        .detail(String.format("%s %s · %s",
                                Objects.toString(row.get(student.lastName), ""),
                                Objects.toString(row.get(student.firstName), ""),
                                XofFormat.format(row.get(intent.amountSchool))).trim())
                        .occurredAt(row.get(intent.settledAt))
                        .link("/app/paiements/transactions")
                        .build())
                .toList();
    }

    /**
     * Échéances arrivées à terme sans règlement, groupées par date.
     *
     * <p>Groupées, et non une par élève : trois cents tranches échues le même jour donneraient trois
     * cents notifications pour un seul fait, et le fil deviendrait illisible le jour où il compte.
     */
    private List<NotificationDto> overdueInstalments(UUID scope, Instant since) {
        QInstallmentEntity installment = QInstallmentEntity.installmentEntity;
        QStudentFeeEntity studentFee = QStudentFeeEntity.studentFeeEntity;
        QStudentEntity student = QStudentEntity.studentEntity;

        LocalDate from = LocalDate.ofInstant(since, ABIDJAN);
        LocalDate today = LocalDate.now(ABIDJAN);

        List<Tuple> rows = this.queryFactory
                .select(installment.dueDate, installment.count(), installment.amount.sum())
                .from(installment)
                .join(installment.studentFee, studentFee)
                .join(studentFee.student, student)
                .where(student.establishment.id.eq(scope),
                        installment.status.eq(InstallmentStatus.PENDING),
                        installment.dueDate.goe(from),
                        installment.dueDate.before(today))
                .groupBy(installment.dueDate)
                .orderBy(installment.dueDate.desc())
                .fetch();

        return rows.stream()
                .map(row -> {
                    LocalDate dueDate = row.get(installment.dueDate);
                    long count = Objects.requireNonNullElse(row.get(installment.count()), 0L);
                    BigDecimal total = Objects.requireNonNullElse(
                            row.get(installment.amount.sum()), BigDecimal.ZERO);
                    return NotificationDto.builder()
                            .id("overdue@" + dueDate)
                            .kind(NotificationKind.INSTALLMENT_OVERDUE)
                            .title("Échéance passée sans règlement")
                            .detail(String.format("%d tranche(s) · %s", count, XofFormat.format(total)))
                            // Le fait se produit à la fin du jour d'échéance : c'est à ce moment
                            // qu'il devient une nouvelle pour l'école.
                            .occurredAt(dueDate.plusDays(1).atStartOfDay(ABIDJAN).toInstant())
                            .link("/app/paiements/relances")
                            .build();
                })
                .toList();
    }

    /* ---------- Côté famille ---------- */

    /**
     * Ce qu'une famille a à savoir.
     *
     * <p>Même doctrine que côté école : rien n'est stocké, chaque élément est déduit d'un fait daté
     * qui existe déjà, et l'état « lu » tient dans la date posée sur le compte — la même colonne
     * pour les deux publics, puisqu'un utilisateur n'est jamais des deux à la fois.
     *
     * <p>Un compte sans enfant rattaché rend une liste vide, et non une erreur : c'est l'état
     * normal d'une famille qui vient de s'inscrire.
     */
    private List<NotificationDto> familyItems(UUID parentId, Instant since) {
        List<StudentEntity> children = this.studentRepository.findByParentUsers_Id(parentId);
        if (children.isEmpty()) {
            return List.of();
        }

        List<NotificationDto> items = new ArrayList<>();
        items.addAll(this.instalmentsOf(children, since));
        items.addAll(this.receiptsOf(children, since));
        items.addAll(this.eventsOf(children, since));
        return items;
    }

    /**
     * Échéances des enfants : celles qui approchent, celles qui sont passées.
     *
     * <p>Une ligne par tranche et non un groupe par date, à l'inverse du fil de l'école : une
     * famille a trois enfants, pas trois cents, et ce qu'elle cherche à savoir est justement
     * <em>lequel</em>.
     *
     * <p>Une tranche à venir n'apparaît qu'à partir du moment où le rappel automatique est parti —
     * J-7 puis J-1 — et n'apparaît qu'une fois, au plus récent des deux. La cloche dit alors la
     * même chose que la notification déjà reçue sur le téléphone, ce qui est le seul moyen que le
     * parent les rapproche au lieu de les compter deux fois.
     */
    private List<NotificationDto> instalmentsOf(List<StudentEntity> children, Instant since) {
        QInstallmentEntity installment = QInstallmentEntity.installmentEntity;
        QStudentFeeEntity studentFee = QStudentFeeEntity.studentFeeEntity;
        QStudentEntity student = QStudentEntity.studentEntity;

        LocalDate today = LocalDate.now(ABIDJAN);
        LocalDate from = LocalDate.ofInstant(since, ABIDJAN);
        List<UUID> ids = children.stream().map(StudentEntity::getId).toList();

        return this.queryFactory
                .select(installment.id, installment.label, installment.amount, installment.dueDate,
                        student.firstName, student.lastName)
                .from(installment)
                .join(installment.studentFee, studentFee)
                .join(studentFee.student, student)
                .where(student.id.in(ids),
                        installment.status.eq(InstallmentStatus.PENDING),
                        installment.dueDate.goe(from),
                        installment.dueDate.loe(today.plusDays(REMINDER_OFFSETS.get(0))))
                .orderBy(installment.dueDate.asc())
                .limit(MAX_ITEMS)
                .fetch().stream()
                .map(row -> {
                    LocalDate dueDate = row.get(installment.dueDate);
                    String detail = String.format("%s — %s · %s",
                            Objects.toString(row.get(installment.label), "Échéance"),
                            nameOf(row.get(student.firstName), row.get(student.lastName)),
                            XofFormat.format(row.get(installment.amount)));
                    long daysLeft = ChronoUnit.DAYS.between(today, dueDate);

                    if (daysLeft < 0) {
                        return NotificationDto.builder()
                                .id("overdue@" + row.get(installment.id))
                                .kind(NotificationKind.INSTALLMENT_OVERDUE)
                                .title("Échéance dépassée")
                                .detail(detail)
                                // Le fait se produit à la fin du jour d'échéance.
                                .occurredAt(dueDate.plusDays(1).atStartOfDay(ABIDJAN).toInstant())
                                .build();
                    }

                    // Le rappel le plus récent *déjà parti* — et non le plus proche de l'échéance.
                    // Un rappel se déclenche à REMINDER_HOUR : entre minuit et cette heure, celui du
                    // jour n'est pas encore émis. Le stamper quand même le datait dans le futur, et
                    // markSeen — qui horodate « maintenant » — ne pouvait alors jamais le couvrir :
                    // l'élément restait non lu jusqu'à l'heure du rappel. On ne retient donc que les
                    // rappels dont l'instant est passé, et le plus récent d'entre eux. Aucun encore
                    // parti : l'élève n'a rien reçu, l'élément n'a pas lieu d'être.
                    Instant now = Instant.now();
                    return REMINDER_OFFSETS.stream()
                            .filter(offset -> offset >= daysLeft)
                            .map(offset -> dueDate.minusDays(offset)
                                    .atTime(REMINDER_HOUR, 0).atZone(ABIDJAN).toInstant())
                            .filter(sentAt -> !sentAt.isAfter(now))
                            .max(Comparator.naturalOrder())
                            .map(sentAt -> NotificationDto.builder()
                                    .id("due@" + row.get(installment.id))
                                    .kind(NotificationKind.INSTALLMENT_DUE_SOON)
                                    .title(titleOf(daysLeft))
                                    .detail(detail)
                                    .occurredAt(sentAt)
                                    .build())
                            .orElse(null);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Reçus émis pour les enfants du compte : la preuve que le paiement est allé au bout.
     *
     * <p>Le rattachement passe par la tentative de paiement — le reçu ne peut pas exister sans elle
     * — et non par le matricule que le reçu conserve en clair. Ce dernier n'est unique qu'au sein
     * d'une école : rapprocher dessus ferait remonter le reçu d'une autre famille le jour où deux
     * établissements réutilisent le même numéro, ce qui est la règle et non l'exception.
     */
    private List<NotificationDto> receiptsOf(List<StudentEntity> children, Instant since) {
        QReceiptEntity receipt = QReceiptEntity.receiptEntity;
        QPaymentIntentEntity intent = QPaymentIntentEntity.paymentIntentEntity;
        QInstallmentEntity installment = QInstallmentEntity.installmentEntity;
        QStudentFeeEntity studentFee = QStudentFeeEntity.studentFeeEntity;
        QStudentEntity student = QStudentEntity.studentEntity;

        List<UUID> ids = children.stream().map(StudentEntity::getId).toList();

        return this.queryFactory
                .select(receipt.id, receipt.studentLabel, receipt.amount, receipt.issuedAt,
                        receipt.channel)
                .from(receipt)
                .join(receipt.paymentIntent, intent)
                .join(intent.installment, installment)
                .join(installment.studentFee, studentFee)
                .join(studentFee.student, student)
                .where(student.id.in(ids), receipt.issuedAt.after(since))
                .orderBy(receipt.issuedAt.desc())
                .limit(MAX_ITEMS)
                .fetch().stream()
                .map(row -> NotificationDto.builder()
                        .id("receipt@" + row.get(receipt.id))
                        .kind(NotificationKind.PAYMENT_CONFIRMED)
                        .title("Paiement confirmé")
                        .detail(String.format("%s · %s %s",
                                Objects.toString(row.get(receipt.studentLabel), "—"),
                                XofFormat.format(row.get(receipt.amount)),
                                channelOf(row.get(receipt.channel))).trim())
                        .occurredAt(row.get(receipt.issuedAt))
                        .build())
                .toList();
    }

    /**
     * Événements que l'école a publiés à l'intention des familles.
     *
     * <p>Datés de leur <em>publication</em> et non de leur tenue : c'est le jour où l'école les
     * annonce que le parent a quelque chose à apprendre. Les dater de l'événement lui-même les
     * ferait apparaître comme non lus longtemps après avoir été vus, et disparaître du fil la
     * veille du jour où ils comptent.
     */
    private List<NotificationDto> eventsOf(List<StudentEntity> children, Instant since) {
        Set<UUID> schools = children.stream()
                .map(StudentEntity::getEstablishment)
                .filter(Objects::nonNull)
                .map(EstablishmentEntity::getId)
                .collect(Collectors.toSet());
        if (schools.isEmpty()) {
            return List.of();
        }
        Set<UUID> classes = children.stream()
                .map(StudentEntity::getSchoolClass)
                .filter(Objects::nonNull)
                .map(SchoolClassEntity::getId)
                .collect(Collectors.toSet());

        QSchoolEventEntity event = QSchoolEventEntity.schoolEventEntity;

        return this.queryFactory
                .selectFrom(event)
                .where(event.establishment.id.in(schools),
                        event.visibleToFamilies.isTrue(),
                        event.createdAt.after(since))
                .orderBy(event.createdAt.desc())
                .limit(MAX_ITEMS)
                .fetch().stream()
                // Le ciblage par classe se lit sur l'entité et non en base : la table de liaison
                // rendrait une ligne par classe, et l'événement apparaîtrait autant de fois.
                .filter(entity -> CalendarService.concerns(entity, classes))
                .map(entity -> NotificationDto.builder()
                        .id("event@" + entity.getId())
                        .kind(NotificationKind.SCHOOL_EVENT)
                        .title(entity.getTitle())
                        .detail(detailOf(entity))
                        .occurredAt(entity.getCreatedAt())
                        .build())
                .toList();
    }

    /** « Échéance demain », comme le dit déjà le rappel automatique parti sur le téléphone. */
    private static String titleOf(long daysLeft) {
        if (daysLeft == 0) {
            return "Échéance aujourd'hui";
        }
        return daysLeft <= 1 ? "Échéance demain" : "Échéance dans " + daysLeft + " jours";
    }

    private static String nameOf(String firstName, String lastName) {
        return String.format("%s %s", Objects.toString(lastName, ""),
                Objects.toString(firstName, "")).trim();
    }

    /** Le canal, dit comme un parent le dirait — « en ligne », pas {@code ONLINE}. */
    private static String channelOf(PaymentChannel channel) {
        if (Objects.isNull(channel)) {
            return "";
        }
        return switch (channel) {
            case ONLINE -> "en ligne";
            case CASH -> "au guichet";
            case CHECK -> "par chèque";
            case BANK_TRANSFER -> "par virement";
        };
    }

    private static String detailOf(SchoolEventEntity entity) {
        StringBuilder detail = new StringBuilder();
        if (Objects.nonNull(entity.getEstablishment())) {
            detail.append(entity.getEstablishment().getName()).append(" — ");
        }
        detail.append("le ").append(entity.getDate().format(DAY));
        if (!entity.isAllDay() && Objects.nonNull(entity.getStartTime())) {
            detail.append(" à ").append(entity.getStartTime().format(HOUR));
        }
        if (Objects.nonNull(entity.getDetails()) && !entity.getDetails().isBlank()) {
            detail.append(". ").append(entity.getDetails().trim());
        }
        return detail.toString();
    }

    private UUID scope() {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        return scope;
    }
}
