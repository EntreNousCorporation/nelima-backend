package com.ypyit.neoelima.domain.canteen.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.canteen.dto.CanteenDtos;
import com.ypyit.neoelima.domain.canteen.entity.CanteenAttendanceEntity;
import com.ypyit.neoelima.domain.canteen.entity.CanteenFormulaEntity;
import com.ypyit.neoelima.domain.canteen.entity.CanteenSubscriptionEntity;
import com.ypyit.neoelima.domain.canteen.entity.StudentDietEntity;
import com.ypyit.neoelima.domain.canteen.enums.CanteenAttendanceStatus;
import com.ypyit.neoelima.domain.canteen.enums.DietKind;
import com.ypyit.neoelima.domain.canteen.form.CanteenForms;
import com.ypyit.neoelima.domain.canteen.repository.CanteenAttendanceRepository;
import com.ypyit.neoelima.domain.canteen.repository.CanteenFormulaRepository;
import com.ypyit.neoelima.domain.canteen.repository.CanteenMenuDayRepository;
import com.ypyit.neoelima.common.service.push.PushNotificationService;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.canteen.repository.CanteenSubscriptionRepository;
import com.ypyit.neoelima.domain.canteen.repository.StudentDietRepository;
import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEnrollmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.enums.StudentPaymentStatus;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentEnrollmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.AcademicYearContext;
import com.ypyit.neoelima.domain.establishment.service.MonthlyBilling;
import com.ypyit.neoelima.domain.establishment.service.SchoolYearGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * La cantine d'une école : formules, abonnements, pointage, chiffres du jour.
 *
 * <p>La facturation est mensuelle. S'abonner crée la dette de l'élève : une tranche par mois, de son
 * mois d'arrivée à la fin de l'année. Mettre fin à l'abonnement annule les mois qui suivent ; ce
 * qui a été servi reste dû.
 *
 * <p>Le pointage n'enregistre que les exceptions : un abonné est servi les jours de sa formule, sauf
 * absence signalée ; un élève ponctuel est compté quand on le marque servi. Pointer six cents repas
 * un par un chaque midi ne serait jamais fait.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CanteenService {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final CanteenFormulaRepository formulaRepository;
    private final CanteenSubscriptionRepository subscriptionRepository;
    private final CanteenAttendanceRepository attendanceRepository;
    private final CanteenMenuDayRepository menuRepository;
    private final PushNotificationService pushNotificationService;
    private final StudentDietRepository dietRepository;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final EstablishmentRepository establishmentRepository;
    private final FeeRepository feeRepository;
    private final StudentFeeRepository studentFeeRepository;
    private final InstallmentRepository installmentRepository;
    private final AcademicYearContext academicYearContext;
    private final MonthlyBilling monthlyBilling;
    private final SchoolYearGuard schoolYearGuard;
    private final CurrentUserProvider currentUserProvider;

    /* ------------------------------------------------------------------ Formules ---- */

    public List<CanteenDtos.Formula> formulas() {
        UUID scope = this.scope();
        List<CanteenSubscriptionEntity> subscriptions = this.subscriptionsOfYear(scope);
        LocalDate today = LocalDate.now(ABIDJAN);
        return this.formulasOfYear(scope).stream()
                .map(formula -> toDto(formula, subscriptions.stream()
                        .filter(s -> s.getFormula().getId().equals(formula.getId()) && s.activeOn(today)).count()))
                .toList();
    }

    @Transactional
    public CanteenDtos.Formula createFormula(CanteenForms.Formula form) {
        UUID scope = this.scope();
        EstablishmentEntity establishment = this.establishmentRepository.findById(scope)
                .orElseThrow(() -> new NotFoundException("Establishment not found"));
        AcademicYearEntity year = this.academicYearContext.consulted().orElse(null);
        this.assertNameFree(scope, form.getName(), null);
        validatePricing(form);
        // Le frais porte le tarif dans la comptabilité de l'école ; c'est l'abonnement qui crée la
        // dette, jamais l'appartenance à un niveau.
        FeeEntity fee = this.feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Cantine — " + form.getName().trim())
                .price(priceOf(form))
                .optional(true)
                .academical(false)
                .establishment(establishment)
                .academicYear(year)
                .build());
        CanteenFormulaEntity formula = this.formulaRepository.saveAndFlush(CanteenFormulaEntity.builder()
                .establishment(establishment)
                .academicYear(year)
                .name(form.getName().trim())
                .days(joinDays(form.getDays()))
                .monthlyPrice(Objects.requireNonNullElse(form.getMonthlyPrice(), BigDecimal.ZERO))
                .mealPrice(form.getMealPrice())
                .fee(fee)
                .active(form.isActive())
                .build());
        return toDto(formula, 0);
    }

    /** Le nouveau tarif vaut pour les abonnements à venir : les mois déjà facturés ne bougent pas. */
    @Transactional
    public CanteenDtos.Formula updateFormula(UUID id, CanteenForms.Formula form) {
        CanteenFormulaEntity formula = this.loadFormula(id);
        this.schoolYearGuard.assertWritable(formula.getEstablishment().getId(), formula.getAcademicYear());
        this.assertNameFree(formula.getEstablishment().getId(), form.getName(), id);
        validatePricing(form);
        if (formula.perMeal() != Objects.nonNull(form.getMealPrice())) {
            // Les abonnés d'une formule mensuelle ont leurs mois déjà facturés : la changer en
            // formule au repas (ou l'inverse) laisserait des dettes incohérentes.
            throw new BadRequestException("Une formule ne passe pas du forfait mensuel au repas : créez-en une nouvelle.");
        }
        formula.setName(form.getName().trim());
        formula.setDays(joinDays(form.getDays()));
        formula.setMonthlyPrice(Objects.requireNonNullElse(form.getMonthlyPrice(), BigDecimal.ZERO));
        formula.setMealPrice(form.getMealPrice());
        formula.setActive(form.isActive());
        if (Objects.nonNull(formula.getFee())) {
            formula.getFee().setName("Cantine — " + formula.getName());
            formula.getFee().setPrice(priceOf(form));
            this.feeRepository.save(formula.getFee());
        }
        return toDto(this.formulaRepository.saveAndFlush(formula), 0);
    }

    /* ------------------------------------------------------------------ Abonnements ---- */

    @Transactional
    public CanteenDtos.Subscriber subscribe(CanteenForms.Subscription form) {
        UUID scope = this.scope();
        StudentEntity student = this.studentRepository.findById(form.getStudentId())
                .orElseThrow(() -> new NotFoundException("Student not found"));
        this.currentUserProvider.assertCanAccessStudent(student);
        CanteenFormulaEntity formula = this.loadFormula(form.getFormulaId());
        if (!formula.isActive()) {
            throw new BadRequestException(String.format("La formule %s n'est plus proposée.", formula.getName()));
        }
        LocalDate start = Objects.requireNonNullElse(form.getStartDate(), LocalDate.now(ABIDJAN));
        boolean already = this.subscriptionRepository.findByStudent_Id(student.getId()).stream()
                .anyMatch(s -> Objects.isNull(s.getEndDate()) || !s.getEndDate().isBefore(start));
        if (already) {
            throw new BadRequestException(String.format(
                    "%s %s est déjà inscrit à la cantine : mettez fin à son abonnement avant d'en changer.",
                    student.getLastName(), student.getFirstName()));
        }
        AcademicYearEntity year = formula.getAcademicYear();
        StudentFeeEntity studentFee = this.studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .student(student).fee(formula.getFee()).name(formula.getFee().getName()).build());
        if (!formula.perMeal()) {
            this.monthlyBilling.bill(studentFee, formula.getName(), formula.getMonthlyPrice(), start, year);
        }

        CanteenSubscriptionEntity subscription = this.subscriptionRepository.saveAndFlush(CanteenSubscriptionEntity.builder()
                .student(student).formula(formula).academicYear(year).startDate(start).studentFee(studentFee).build());
        if (form.isNotifyFamily()) {
            this.notifyFamily(student, formula, start);
        }
        log.info("CANTEEN_SUBSCRIBED: student {} formula {} in establishment {}", student.getId(), formula.getName(), scope);
        return this.subscribers(start, List.of(subscription)).getFirst();
    }

    /** Fin d'abonnement : les mois qui suivent celui du dernier repas sont annulés s'ils ne sont pas réglés. */
    @Transactional
    public void end(UUID subscriptionId, CanteenForms.End form) {
        CanteenSubscriptionEntity subscription = this.loadSubscription(subscriptionId);
        LocalDate end = Objects.requireNonNullElse(form.getEndDate(), LocalDate.now(ABIDJAN));
        if (end.isBefore(subscription.getStartDate())) {
            throw new BadRequestException("La fin d'abonnement précède son début.");
        }
        subscription.setEndDate(end);
        this.subscriptionRepository.save(subscription);
        this.monthlyBilling.stopAfter(subscription.getStudentFee(), end);
    }

    /** Les abonnés de l'année, et ce qu'il en est d'eux ce jour-là. */
    public List<CanteenDtos.Subscriber> subscribers(LocalDate day) {
        UUID scope = this.scope();
        LocalDate date = Objects.requireNonNullElse(day, LocalDate.now(ABIDJAN));
        List<CanteenSubscriptionEntity> subscriptions = this.subscriptionsOfYear(scope).stream()
                .filter(s -> s.activeOn(date) || date.isBefore(s.getStartDate()))
                .sorted(Comparator.comparing((CanteenSubscriptionEntity s) -> s.getStudent().getLastName())
                        .thenComparing(s -> s.getStudent().getFirstName()))
                .toList();
        return this.subscribers(date, subscriptions);
    }

    /* ------------------------------------------------------------------ Pointage ---- */

    @Transactional
    public void mark(CanteenForms.Attendance form) {
        StudentEntity student = this.studentRepository.findById(form.getStudentId())
                .orElseThrow(() -> new NotFoundException("Student not found"));
        this.currentUserProvider.assertCanAccessStudent(student);
        Optional<CanteenAttendanceEntity> existing = this.attendanceRepository.findByStudent_IdAndDay(student.getId(), form.getDay());
        if (Objects.isNull(form.getStatus())) {
            existing.ifPresent(this.attendanceRepository::delete);
        } else {
            CanteenAttendanceEntity attendance = existing.orElseGet(() -> CanteenAttendanceEntity.builder()
                    .student(student).day(form.getDay()).build());
            attendance.setStatus(form.getStatus());
            this.attendanceRepository.save(attendance);
        }
        this.attendanceRepository.flush();
        this.billMeals(student, form.getDay());
    }

    /**
     * Facture le mois d'un élève à formule au repas : une tranche « repas de <mois> » qui suit les
     * pointages tant qu'elle n'est pas réglée.
     *
     * <p>Le montant dû est toujours recalculé depuis les repas servis du mois, moins ce qui en a déjà
     * été réglé : marquer puis démarquer un repas ne laisse aucune trace, et un repas pointé après
     * le règlement fait naître une tranche complémentaire. Payable le 5 du mois suivant, comme les
     * forfaits mensuels.
     */
    private void billMeals(StudentEntity student, LocalDate day) {
        Optional<CanteenSubscriptionEntity> subscription = this.subscriptionRepository.findByStudent_Id(student.getId()).stream()
                .filter(s -> s.activeOn(day) && s.getFormula().perMeal() && Objects.nonNull(s.getStudentFee()))
                .findFirst();
        if (subscription.isEmpty()) {
            return;
        }
        CanteenFormulaEntity formula = subscription.get().getFormula();
        StudentFeeEntity studentFee = subscription.get().getStudentFee();
        java.time.YearMonth month = java.time.YearMonth.from(day);
        long meals = this.attendanceRepository.findByStudent_IdAndDayBetween(student.getId(), month.atDay(1), month.atEndOfMonth())
                .stream().filter(a -> a.getStatus() == CanteenAttendanceStatus.SERVED).count();
        String label = String.format("%s — repas de %s %d", formula.getName(),
                month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRENCH), month.getYear());

        List<InstallmentEntity> lines = this.installmentRepository.findByStudentFee_Id(studentFee.getId()).stream()
                .filter(i -> label.equals(i.getLabel())).toList();
        BigDecimal settled = lines.stream().filter(i -> i.getStatus() == InstallmentStatus.PAID)
                .map(InstallmentEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal due = formula.getMealPrice().multiply(BigDecimal.valueOf(meals)).subtract(settled);
        List<InstallmentEntity> pending = lines.stream().filter(i -> i.getStatus() == InstallmentStatus.PENDING).toList();

        if (due.signum() > 0) {
            InstallmentEntity open = pending.isEmpty()
                    ? InstallmentEntity.builder().studentFee(studentFee).label(label)
                            .dueDate(month.plusMonths(1).atDay(5)).status(InstallmentStatus.PENDING).build()
                    : pending.getFirst();
            open.setAmount(due);
            this.installmentRepository.save(open);
            pending.stream().skip(1).forEach(this::cancel);
        } else {
            pending.forEach(this::cancel);
        }
    }

    private void cancel(InstallmentEntity installment) {
        installment.setStatus(InstallmentStatus.CANCELLED);
        this.installmentRepository.save(installment);
    }

    /** Un forfait mensuel, ou un prix au repas sans jour fixe : jamais les deux, jamais aucun. */
    private static void validatePricing(CanteenForms.Formula form) {
        boolean monthly = Objects.nonNull(form.getMonthlyPrice()) && form.getMonthlyPrice().signum() > 0;
        boolean perMeal = Objects.nonNull(form.getMealPrice());
        if (perMeal && monthly) {
            throw new BadRequestException("Une formule est au mois ou au repas, pas les deux.");
        }
        if (!perMeal && Objects.isNull(form.getMonthlyPrice())) {
            throw new BadRequestException("Indiquez le tarif mensuel, ou un prix par repas.");
        }
        if (perMeal && Objects.nonNull(form.getDays()) && !form.getDays().isEmpty()) {
            throw new BadRequestException("Une formule au repas n'a pas de jours fixes : l'élève est facturé les jours où il mange.");
        }
    }

    private static BigDecimal priceOf(CanteenForms.Formula form) {
        return Objects.nonNull(form.getMealPrice()) ? form.getMealPrice()
                : Objects.requireNonNullElse(form.getMonthlyPrice(), BigDecimal.ZERO);
    }

    /* ------------------------------------------------------------------ Chiffres ---- */

    public CanteenDtos.Overview overview(LocalDate day) {
        UUID scope = this.scope();
        LocalDate date = Objects.requireNonNullElse(day, LocalDate.now(ABIDJAN));
        List<CanteenSubscriptionEntity> active = this.subscriptionsOfYear(scope).stream()
                .filter(s -> s.activeOn(date)).toList();
        List<CanteenDtos.Subscriber> rows = this.subscribers(date, active);

        UUID year = this.academicYearContext.consultedId();
        long enrolled = Objects.isNull(year) ? this.studentRepository.findAllByEstablishment_Id(scope, org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements()
                : this.enrollmentRepository.countByAcademicYear_IdAndStudent_Establishment_Id(year, scope);

        Set<UUID> studentIds = active.stream().map(s -> s.getStudent().getId()).collect(Collectors.toSet());
        List<StudentDietEntity> diets = studentIds.isEmpty() ? List.of() : this.dietRepository.findByStudent_IdIn(studentIds);
        Map<DietKind, Long> byKind = new EnumMap<>(DietKind.class);
        diets.forEach(diet -> byKind.merge(diet.getKind(), 1L, Long::sum));

        // Impayés : les mois échus et non réglés des abonnements de l'année.
        List<InstallmentEntity> pending = this.pendingCanteenInstallments(scope);
        List<InstallmentEntity> overdue = pending.stream()
                .filter(i -> Objects.nonNull(i.getDueDate()) && i.getDueDate().isBefore(date)).toList();

        return CanteenDtos.Overview.builder()
                .day(date)
                .subscribers(active.size())
                .shareOfEnrollment(enrolled == 0 ? 0 : (int) Math.round(active.size() * 100.0 / enrolled))
                .expectedToday(rows.stream().filter(CanteenDtos.Subscriber::isExpectedToday).count())
                .servedToday(rows.stream().filter(r -> r.getToday() == CanteenAttendanceStatus.SERVED).count())
                .absentToday(rows.stream().filter(r -> r.getToday() == CanteenAttendanceStatus.ABSENT).count())
                .studentsWithDiet(diets.stream().map(d -> d.getStudent().getId()).distinct().count())
                .allergies(diets.stream().filter(d -> d.getKind().isAllergy()).count())
                .unpaidAmount(overdue.stream().map(InstallmentEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .unpaidStudents(overdue.stream().map(i -> i.getStudentFee().getStudent().getId()).distinct().count())
                .nextDueDate(pending.stream().map(InstallmentEntity::getDueDate).filter(Objects::nonNull)
                        .filter(d -> !d.isBefore(date)).min(Comparator.naturalOrder()).orElse(null))
                .diets(byKind.entrySet().stream()
                        .map(e -> CanteenDtos.DietCount.builder().kind(e.getKey()).count(e.getValue()).build())
                        .sorted(Comparator.comparing(CanteenDtos.DietCount::getCount).reversed())
                        .toList())
                .build();
    }

    /** Fiche cuisine : pour chaque régime, les élèves attendus ce jour-là. */
    public List<CanteenDtos.KitchenLine> kitchenSheet(LocalDate day) {
        LocalDate date = Objects.requireNonNullElse(day, LocalDate.now(ABIDJAN));
        List<CanteenDtos.Subscriber> eating = this.subscribers(date).stream()
                .filter(r -> r.getToday() == CanteenAttendanceStatus.SERVED).toList();
        Map<DietKind, List<CanteenDtos.KitchenStudent>> byKind = new EnumMap<>(DietKind.class);
        for (CanteenDtos.Subscriber row : eating) {
            for (CanteenDtos.Diet diet : row.getDiets()) {
                byKind.computeIfAbsent(diet.getKind(), k -> new ArrayList<>()).add(CanteenDtos.KitchenStudent.builder()
                        .name(row.getStudentName()).className(row.getClassName()).label(diet.getLabel()).build());
            }
        }
        return byKind.entrySet().stream()
                .map(e -> CanteenDtos.KitchenLine.builder().kind(e.getKey()).students(e.getValue()).build())
                .toList();
    }

    /* ------------------------------------------------------------------ Outils ---- */

    private List<CanteenDtos.Subscriber> subscribers(LocalDate date, List<CanteenSubscriptionEntity> subscriptions) {
        if (subscriptions.isEmpty()) {
            return List.of();
        }
        UUID scope = subscriptions.getFirst().getStudent().getEstablishment().getId();
        List<UUID> studentIds = subscriptions.stream().map(s -> s.getStudent().getId()).toList();

        Map<UUID, List<StudentDietEntity>> diets = this.dietRepository.findByStudent_IdIn(studentIds).stream()
                .collect(Collectors.groupingBy(d -> d.getStudent().getId()));
        Map<UUID, CanteenAttendanceStatus> marks = new HashMap<>();
        this.attendanceRepository.findByStudent_Establishment_IdAndDay(scope, date)
                .forEach(a -> marks.put(a.getStudent().getId(), a.getStatus()));
        Map<UUID, String> classes = this.classNames(studentIds);
        LocalDate today = LocalDate.now(ABIDJAN);
        // Un jour sans service (férié, sortie), personne n'est attendu.
        boolean closed = this.menuRepository.findByEstablishment_IdAndDay(scope, date).map(m -> m.isClosed()).orElse(false);

        List<CanteenDtos.Subscriber> rows = new ArrayList<>();
        for (CanteenSubscriptionEntity s : subscriptions) {
            StudentEntity student = s.getStudent();
            boolean expected = !closed && s.activeOn(date) && s.getFormula().dayOfWeeks().contains(date.getDayOfWeek());
            CanteenAttendanceStatus mark = marks.get(student.getId());
            CanteenAttendanceStatus status = Objects.nonNull(mark) ? mark : expected ? CanteenAttendanceStatus.SERVED : null;
            rows.add(CanteenDtos.Subscriber.builder()
                    .subscriptionId(s.getId().toString())
                    .studentId(student.getId())
                    .studentName(String.format("%s %s", student.getLastName(), student.getFirstName()))
                    .registrationNumber(student.getRegistrationNumber())
                    .photoId(student.getPhotoDocumentId())
                    .className(classes.get(student.getId()))
                    .formulaId(s.getFormula().getId().toString())
                    .formulaName(s.getFormula().getName())
                    .startDate(s.getStartDate())
                    .endDate(s.getEndDate())
                    .diets(diets.getOrDefault(student.getId(), List.of()).stream()
                            .map(d -> CanteenDtos.Diet.builder().kind(d.getKind()).label(d.getLabel()).build()).toList())
                    .paymentStatus(this.paymentStatus(s, today))
                    .today(status)
                    .expectedToday(expected)
                    .build());
        }
        return rows;
    }

    /** Annonce l'inscription aux parents : la formule, ce qu'elle coûte, où trouver le menu. */
    private void notifyFamily(StudentEntity student, CanteenFormulaEntity formula, LocalDate start) {
        Set<UUID> parents = student.getParentUsers().stream().map(UserEntity::getId).collect(Collectors.toSet());
        if (parents.isEmpty()) {
            return;
        }
        String price = String.format(java.util.Locale.FRANCE, "%,.0f", formula.getMonthlyPrice()).replace('\u202f', ' ').replace('\u00a0', ' ');
        this.pushNotificationService.send(parents, "Cantine",
                String.format("%s est inscrit(e) à la cantine à partir du %s : formule %s, %s F par mois. Le menu de la semaine est dans l'application.",
                        student.getFirstName(), start.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")), formula.getName(), price),
                Map.of("studentId", student.getId().toString()));
    }

    /** Statut de paiement de l'abonnement, selon les règles des autres écrans. */
    private StudentPaymentStatus paymentStatus(CanteenSubscriptionEntity subscription, LocalDate today) {
        if (Objects.isNull(subscription.getStudentFee())) {
            return StudentPaymentStatus.UP_TO_DATE;
        }
        List<InstallmentEntity> lines = this.installmentRepository.findByStudentFee_Id(subscription.getStudentFee().getId());
        boolean overdue = lines.stream().anyMatch(i -> i.getStatus() == InstallmentStatus.PENDING
                && Objects.nonNull(i.getDueDate()) && i.getDueDate().isBefore(today));
        if (overdue) {
            return StudentPaymentStatus.OVERDUE;
        }
        boolean paid = lines.stream().anyMatch(i -> i.getStatus() == InstallmentStatus.PAID);
        boolean pending = lines.stream().anyMatch(i -> i.getStatus() == InstallmentStatus.PENDING);
        return paid && pending ? StudentPaymentStatus.DEPOSIT : StudentPaymentStatus.UP_TO_DATE;
    }

    private Map<UUID, String> classNames(Collection<UUID> studentIds) {
        UUID year = this.academicYearContext.consultedId();
        Map<UUID, String> names = new HashMap<>();
        if (Objects.nonNull(year)) {
            for (StudentEnrollmentEntity enrollment : this.enrollmentRepository.findByAcademicYear_IdAndStudent_IdIn(year, studentIds)) {
                if (Objects.nonNull(enrollment.getSchoolClass())) {
                    names.put(enrollment.getStudent().getId(), enrollment.getSchoolClass().getName());
                }
            }
            return names;
        }
        this.studentRepository.findAllById(studentIds).forEach(student -> {
            if (Objects.nonNull(student.getSchoolClass())) {
                names.put(student.getId(), student.getSchoolClass().getName());
            }
        });
        return names;
    }

    private List<InstallmentEntity> pendingCanteenInstallments(UUID scope) {
        return this.subscriptionsOfYear(scope).stream()
                .map(CanteenSubscriptionEntity::getStudentFee).filter(Objects::nonNull)
                .flatMap(fee -> this.installmentRepository.findByStudentFee_Id(fee.getId()).stream())
                .filter(i -> i.getStatus() == InstallmentStatus.PENDING)
                .toList();
    }

    private List<CanteenSubscriptionEntity> subscriptionsOfYear(UUID scope) {
        return this.subscriptionRepository.findByStudent_Establishment_Id(scope).stream()
                .filter(s -> this.academicYearContext.inConsultedYear(s.getAcademicYear()))
                .toList();
    }

    private List<CanteenFormulaEntity> formulasOfYear(UUID scope) {
        return this.formulaRepository.findByEstablishment_IdOrderByNameAsc(scope).stream()
                .filter(f -> this.academicYearContext.inConsultedYear(f.getAcademicYear()))
                .toList();
    }

    private void assertNameFree(UUID scope, String name, UUID except) {
        boolean taken = this.formulasOfYear(scope).stream()
                .anyMatch(f -> !f.getId().equals(except) && f.getName().equalsIgnoreCase(name.trim()));
        if (taken) {
            throw new BadRequestException(String.format("Une formule nommée %s existe déjà.", name.trim()));
        }
    }

    private CanteenFormulaEntity loadFormula(UUID id) {
        UUID scope = this.scope();
        return this.formulaRepository.findById(id)
                .filter(f -> f.getEstablishment().getId().equals(scope))
                .orElseThrow(() -> new NotFoundException("Formule introuvable."));
    }

    private CanteenSubscriptionEntity loadSubscription(UUID id) {
        UUID scope = this.scope();
        return this.subscriptionRepository.findById(id)
                .filter(s -> s.getStudent().getEstablishment().getId().equals(scope))
                .orElseThrow(() -> new NotFoundException("Abonnement introuvable."));
    }

    private UUID scope() {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        return scope;
    }

    private static String joinDays(List<DayOfWeek> days) {
        if (Objects.isNull(days) || days.isEmpty()) {
            return null;
        }
        return days.stream().distinct().sorted().map(DayOfWeek::name).collect(Collectors.joining(","));
    }

    private static CanteenDtos.Formula toDto(CanteenFormulaEntity formula, long subscribers) {
        return CanteenDtos.Formula.builder()
                .id(formula.getId().toString())
                .name(formula.getName())
                .days(formula.dayOfWeeks().stream().sorted().toList())
                .monthlyPrice(formula.getMonthlyPrice())
                .mealPrice(formula.getMealPrice())
                .active(formula.isActive())
                .subscribers(subscribers)
                .build();
    }
}
