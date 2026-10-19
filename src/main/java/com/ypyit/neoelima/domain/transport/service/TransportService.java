package com.ypyit.neoelima.domain.transport.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.common.service.push.PushNotificationService;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
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
import com.ypyit.neoelima.domain.transport.dto.TransportDtos;
import com.ypyit.neoelima.domain.transport.entity.TransportDriverEntity;
import com.ypyit.neoelima.domain.transport.entity.TransportLineEntity;
import com.ypyit.neoelima.domain.transport.entity.TransportLineStatusEntity;
import com.ypyit.neoelima.domain.transport.entity.TransportStopEntity;
import com.ypyit.neoelima.domain.transport.entity.TransportSubscriptionEntity;
import com.ypyit.neoelima.domain.transport.enums.LineStatus;
import com.ypyit.neoelima.domain.transport.form.TransportForms;
import com.ypyit.neoelima.domain.transport.repository.TransportDriverRepository;
import com.ypyit.neoelima.domain.transport.repository.TransportLineRepository;
import com.ypyit.neoelima.domain.transport.repository.TransportLineStatusRepository;
import com.ypyit.neoelima.domain.transport.repository.TransportStopRepository;
import com.ypyit.neoelima.domain.transport.repository.TransportSubscriptionRepository;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Le transport scolaire d'une école : chauffeurs, lignes et arrêts, abonnés, suivi du jour.
 *
 * <p>Une ligne est celle d'une année ; on s'y abonne à un arrêt, et l'abonnement se facture au mois
 * comme la cantine. Le suivi du jour est saisi par l'école : sans application chauffeur, c'est la
 * seule source fiable, et un retard signalé prévient les familles de la ligne.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransportService {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    /** Pièces du dossier d'un chauffeur : permis, pièce d'identité, casier judiciaire, certificat médical. */
    private static final List<String> DRIVER_DOCUMENTS = List.of("LICENSE", "ID", "RECORD", "MEDICAL");

    private final TransportDriverRepository driverRepository;
    private final TransportLineRepository lineRepository;
    private final TransportStopRepository stopRepository;
    private final TransportSubscriptionRepository subscriptionRepository;
    private final TransportLineStatusRepository statusRepository;
    private final EstablishmentRepository establishmentRepository;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final FeeRepository feeRepository;
    private final StudentFeeRepository studentFeeRepository;
    private final InstallmentRepository installmentRepository;
    private final MonthlyBilling monthlyBilling;
    private final AcademicYearContext academicYearContext;
    private final SchoolYearGuard schoolYearGuard;
    private final PushNotificationService pushNotificationService;
    private final CurrentUserProvider currentUserProvider;

    /* ------------------------------------------------------------------ Chauffeurs ---- */

    public List<TransportDtos.Driver> drivers() {
        UUID scope = this.scope();
        Map<UUID, List<String>> linesByDriver = new HashMap<>();
        this.linesOfYear(scope).forEach(line -> {
            if (Objects.nonNull(line.getDriver())) {
                linesByDriver.computeIfAbsent(line.getDriver().getId(), k -> new ArrayList<>()).add(line.getName());
            }
        });
        return this.driverRepository.findByEstablishment_IdOrderByLastNameAsc(scope).stream()
                .map(driver -> toDto(driver, linesByDriver.getOrDefault(driver.getId(), List.of())))
                .toList();
    }

    @Transactional
    public TransportDtos.Driver saveDriver(UUID id, TransportForms.Driver form) {
        UUID scope = this.scope();
        TransportDriverEntity driver = Objects.isNull(id)
                ? TransportDriverEntity.builder().establishment(this.establishmentRepository.getReferenceById(scope)).build()
                : this.loadDriver(id, scope);
        driver.setFirstName(form.getFirstName().trim());
        driver.setLastName(form.getLastName().trim().toUpperCase());
        driver.setPhone(trim(form.getPhone()));
        driver.setLicenseCategory(trim(form.getLicenseCategory()));
        driver.setDocuments(Objects.isNull(form.getDocuments()) || form.getDocuments().isEmpty() ? null
                : form.getDocuments().stream().filter(DRIVER_DOCUMENTS::contains).distinct().collect(Collectors.joining(",")));
        driver.setLicenseExpiry(form.getLicenseExpiry());
        driver.setActive(form.isActive());
        return toDto(this.driverRepository.saveAndFlush(driver), List.of());
    }

    /* ------------------------------------------------------------------ Lignes ---- */

    public List<TransportDtos.Line> lines() {
        UUID scope = this.scope();
        LocalDate today = LocalDate.now(ABIDJAN);
        Map<UUID, TransportLineStatusEntity> statuses = this.statusRepository.findByLine_Establishment_IdAndDay(scope, today)
                .stream().collect(Collectors.toMap(s -> s.getLine().getId(), s -> s));
        List<TransportSubscriptionEntity> subscriptions = this.subscriptionsOfYear(scope);
        return this.linesOfYear(scope).stream()
                .map(line -> this.toDto(line, subscriptions, statuses.get(line.getId()), today))
                .toList();
    }

    @Transactional
    public TransportDtos.Line saveLine(UUID id, TransportForms.Line form) {
        UUID scope = this.scope();
        EstablishmentEntity establishment = this.establishmentRepository.getReferenceById(scope);
        TransportLineEntity line;
        if (Objects.isNull(id)) {
            AcademicYearEntity year = this.academicYearContext.consulted().orElse(null);
            this.assertNameFree(scope, form.getName(), null);
            // Comme pour la cantine : le frais porte le tarif, l'abonnement crée la dette.
            FeeEntity fee = this.feeRepository.saveAndFlush(FeeEntity.builder()
                    .name("Transport — " + form.getName().trim()).price(form.getMonthlyPrice())
                    .optional(true).academical(false).establishment(establishment).academicYear(year).build());
            line = TransportLineEntity.builder().establishment(establishment).academicYear(year).fee(fee).build();
        } else {
            line = this.loadLine(id, scope);
            this.schoolYearGuard.assertWritable(scope, line.getAcademicYear());
            this.assertNameFree(scope, form.getName(), id);
            if (Objects.nonNull(line.getFee())) {
                line.getFee().setName("Transport — " + form.getName().trim());
                line.getFee().setPrice(form.getMonthlyPrice());
                this.feeRepository.save(line.getFee());
            }
        }
        line.setName(form.getName().trim());
        line.setVehiclePlate(trim(form.getVehiclePlate()));
        line.setCapacity(form.getCapacity());
        line.setDriver(Objects.isNull(form.getDriverId()) ? null : this.loadDriver(form.getDriverId(), scope));
        line.setMorningDeparture(form.getMorningDeparture());
        line.setEveningDeparture(form.getEveningDeparture());
        line.setDays(Objects.isNull(form.getDays()) || form.getDays().isEmpty() ? "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY"
                : form.getDays().stream().distinct().sorted().map(Enum::name).collect(Collectors.joining(",")));
        line.setAttendant(trim(form.getAttendant()));
        line.setStartDate(form.getStartDate());
        line.setMonthlyPrice(form.getMonthlyPrice());
        line.setActive(form.isActive());
        TransportLineEntity saved = this.lineRepository.saveAndFlush(line);
        this.replaceStops(saved, form.getStops());
        if (Objects.isNull(id) && form.isAnnounce()) {
            this.announce(saved, scope);
        }
        return this.lines().stream().filter(l -> l.getId().equals(saved.getId().toString())).findFirst().orElseThrow();
    }

    /**
     * Remplace les arrêts dans l'ordre donné. Un arrêt qui garde son identifiant garde ses
     * abonnés ; un arrêt retiré les laisse sans arrêt, sur leur ligne.
     */
    private void replaceStops(TransportLineEntity line, List<TransportForms.Stop> stops) {
        Map<UUID, TransportStopEntity> existing = this.stopRepository.findByLine_IdOrderByPositionAsc(line.getId()).stream()
                .collect(Collectors.toMap(TransportStopEntity::getId, s -> s));
        Set<UUID> kept = new java.util.HashSet<>();
        int position = 1;
        for (TransportForms.Stop form : Objects.requireNonNullElse(stops, List.<TransportForms.Stop>of())) {
            TransportStopEntity stop = Objects.nonNull(form.getId()) && existing.containsKey(form.getId())
                    ? existing.get(form.getId())
                    : TransportStopEntity.builder().line(line).build();
            stop.setPosition(position++);
            stop.setName(form.getName().trim());
            stop.setPickupTime(form.getPickupTime());
            stop.setLatitude(form.getLatitude());
            stop.setLongitude(form.getLongitude());
            TransportStopEntity saved = this.stopRepository.save(stop);
            kept.add(saved.getId());
        }
        existing.values().stream().filter(stop -> !kept.contains(stop.getId())).forEach(stop -> {
            this.subscriptionRepository.findByLine_Id(line.getId()).stream()
                    .filter(s -> Objects.nonNull(s.getStop()) && s.getStop().getId().equals(stop.getId()))
                    .forEach(s -> {
                        s.setStop(null);
                        this.subscriptionRepository.save(s);
                    });
            this.stopRepository.delete(stop);
        });
        this.stopRepository.flush();
    }

    /* ------------------------------------------------------------------ Suivi du jour ---- */

    /** Statut de la ligne aujourd'hui ; un retard ou un incident prévient les familles de la ligne. */
    @Transactional
    public TransportDtos.Line setStatus(UUID lineId, TransportForms.Status form) {
        UUID scope = this.scope();
        TransportLineEntity line = this.loadLine(lineId, scope);
        LocalDate today = LocalDate.now(ABIDJAN);
        TransportLineStatusEntity status = this.statusRepository.findByLine_IdAndDay(lineId, today)
                .orElseGet(() -> TransportLineStatusEntity.builder().line(line).day(today).build());
        status.setStatus(form.getStatus());
        status.setDelayMinutes(form.getStatus() == LineStatus.DELAYED ? form.getDelayMinutes() : null);
        status.setNote(trim(form.getNote()));
        this.statusRepository.saveAndFlush(status);

        boolean alarming = form.getStatus() == LineStatus.DELAYED || form.getStatus() == LineStatus.INCIDENT;
        if (Objects.requireNonNullElse(form.getNotifyFamilies(), alarming)) {
            this.notifyFamilies(line, status, today);
        }
        return this.lines().stream().filter(l -> l.getId().equals(lineId.toString())).findFirst().orElseThrow();
    }

    /** Une ligne qui ouvre : les familles de l'école l'apprennent et peuvent demander une place. */
    private void announce(TransportLineEntity line, UUID scope) {
        Set<UUID> parents = this.studentRepository.findAllByEstablishment_Id(scope, org.springframework.data.domain.Pageable.unpaged())
                .stream().flatMap(student -> student.getParentUsers().stream()).map(UserEntity::getId).collect(Collectors.toSet());
        if (parents.isEmpty()) {
            return;
        }
        List<String> stops = this.stopRepository.findByLine_IdOrderByPositionAsc(line.getId()).stream().map(TransportStopEntity::getName).toList();
        this.pushNotificationService.send(parents, "Transport scolaire", String.format("Nouvelle ligne de car : %s%s. Inscriptions ouvertes%s.",
                        line.getName(), stops.isEmpty() ? "" : " (" + String.join(", ", stops) + ")",
                        Objects.isNull(line.getStartDate()) ? "" : " dès le " + line.getStartDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))),
                Map.of("lineId", line.getId().toString()));
        log.info("TRANSPORT_LINE_ANNOUNCED: {} ({} parents)", line.getName(), parents.size());
    }

    private void notifyFamilies(TransportLineEntity line, TransportLineStatusEntity status, LocalDate today) {
        Set<UUID> parents = this.subscriptionRepository.findByLine_Id(line.getId()).stream()
                .filter(s -> s.activeOn(today))
                .flatMap(s -> s.getStudent().getParentUsers().stream())
                .map(UserEntity::getId)
                .collect(Collectors.toSet());
        if (parents.isEmpty()) {
            return;
        }
        String message = switch (status.getStatus()) {
            case DELAYED -> String.format("Le car de la %s a %s de retard.", line.getName(),
                    Objects.isNull(status.getDelayMinutes()) ? "du" : status.getDelayMinutes() + " min");
            case INCIDENT -> String.format("Incident sur la %s%s", line.getName(),
                    Objects.isNull(status.getNote()) ? "." : " : " + status.getNote());
            case ARRIVED -> String.format("Le car de la %s est arrivé à l'école.", line.getName());
            case EN_ROUTE -> String.format("Le car de la %s est en route.", line.getName());
            case NOT_STARTED -> String.format("Le car de la %s n'est pas encore parti.", line.getName());
        };
        this.pushNotificationService.send(parents, "Transport scolaire", message, Map.of("lineId", line.getId().toString()));
        log.info("TRANSPORT_FAMILIES_NOTIFIED: line {} status {} ({} parents)", line.getName(), status.getStatus(), parents.size());
    }

    /* ------------------------------------------------------------------ Abonnés ---- */

    public List<TransportDtos.Rider> riders(UUID lineId) {
        UUID scope = this.scope();
        LocalDate today = LocalDate.now(ABIDJAN);
        List<TransportSubscriptionEntity> subscriptions = this.subscriptionsOfYear(scope).stream()
                .filter(s -> Objects.isNull(lineId) || s.getLine().getId().equals(lineId))
                .filter(s -> s.activeOn(today) || today.isBefore(s.getStartDate()))
                .sorted(Comparator.comparing((TransportSubscriptionEntity s) -> s.getLine().getName())
                        .thenComparing(s -> s.getStudent().getLastName()))
                .toList();
        Map<UUID, String> classes = this.classNames(subscriptions.stream().map(s -> s.getStudent().getId()).toList());
        return subscriptions.stream().map(s -> TransportDtos.Rider.builder()
                .subscriptionId(s.getId().toString())
                .billingPeriod(s.getBillingPeriod())
                .studentId(s.getStudent().getId())
                .studentName(String.format("%s %s", s.getStudent().getLastName(), s.getStudent().getFirstName()))
                .registrationNumber(s.getStudent().getRegistrationNumber())
                .photoId(s.getStudent().getPhotoDocumentId())
                .className(classes.get(s.getStudent().getId()))
                .lineId(s.getLine().getId().toString())
                .lineName(s.getLine().getName())
                .stopId(Objects.isNull(s.getStop()) ? null : s.getStop().getId().toString())
                .stopName(Objects.isNull(s.getStop()) ? null : s.getStop().getName())
                .pickupTime(Objects.isNull(s.getStop()) ? null : s.getStop().getPickupTime())
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
                .paymentStatus(this.paymentStatus(s.getStudentFee(), today))
                .build()).toList();
    }

    @Transactional
    public TransportDtos.Rider subscribe(TransportForms.Subscription form) {
        UUID scope = this.scope();
        StudentEntity student = this.studentRepository.findById(form.getStudentId())
                .orElseThrow(() -> new NotFoundException("Student not found"));
        this.currentUserProvider.assertCanAccessStudent(student);
        TransportLineEntity line = this.loadLine(form.getLineId(), scope);
        if (!line.isActive()) {
            throw new BadRequestException(String.format("La %s n'est plus en service.", line.getName()));
        }
        LocalDate requested = Objects.requireNonNullElse(form.getStartDate(), LocalDate.now(ABIDJAN));
        LocalDate start = Objects.nonNull(line.getStartDate()) && requested.isBefore(line.getStartDate()) ? line.getStartDate() : requested;
        boolean already = this.subscriptionRepository.findByStudent_Id(student.getId()).stream()
                .anyMatch(s -> Objects.isNull(s.getEndDate()) || !s.getEndDate().isBefore(start));
        if (already) {
            throw new BadRequestException(String.format(
                    "%s %s est déjà inscrit au transport : mettez fin à son abonnement avant d'en changer.",
                    student.getLastName(), student.getFirstName()));
        }
        // Un car a le nombre de places qu'il a : au-delà, on refuse plutôt que de surcharger.
        long riders = this.subscriptionRepository.findByLine_Id(line.getId()).stream().filter(s -> s.activeOn(start)).count();
        if (riders >= line.getCapacity()) {
            throw new BadRequestException(String.format("La %s est complète (%d places).", line.getName(), line.getCapacity()));
        }
        TransportStopEntity stop = null;
        if (Objects.nonNull(form.getStopId())) {
            stop = this.stopRepository.findById(form.getStopId())
                    .filter(s -> s.getLine().getId().equals(line.getId()))
                    .orElseThrow(() -> new BadRequestException("Cet arrêt n'appartient pas à la ligne."));
        }
        StudentFeeEntity studentFee = this.studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .student(student).fee(line.getFee()).name(line.getFee().getName()).build());
        com.ypyit.neoelima.domain.establishment.enums.BillingPeriod period = Objects.requireNonNullElse(form.getBillingPeriod(), com.ypyit.neoelima.domain.establishment.enums.BillingPeriod.MONTHLY);
        if (period == com.ypyit.neoelima.domain.establishment.enums.BillingPeriod.TERM) {
            this.monthlyBilling.billByTerm(studentFee, "Transport " + line.getName(), line.getMonthlyPrice(), start, this.termsOf(scope, line.getAcademicYear()));
        } else {
            this.monthlyBilling.bill(studentFee, "Transport " + line.getName(), line.getMonthlyPrice(), start, line.getAcademicYear());
        }
        TransportSubscriptionEntity saved = this.subscriptionRepository.saveAndFlush(TransportSubscriptionEntity.builder()
                .student(student).line(line).stop(stop).academicYear(line.getAcademicYear())
                .startDate(start).billingPeriod(period).studentFee(studentFee).build());
        log.info("TRANSPORT_SUBSCRIBED: student {} line {}", student.getId(), line.getName());
        return this.riders(line.getId()).stream()
                .filter(r -> r.getSubscriptionId().equals(saved.getId().toString())).findFirst().orElseThrow();
    }

    @Transactional
    public void end(UUID subscriptionId, TransportForms.End form) {
        UUID scope = this.scope();
        TransportSubscriptionEntity subscription = this.subscriptionRepository.findById(subscriptionId)
                .filter(s -> s.getStudent().getEstablishment().getId().equals(scope))
                .orElseThrow(() -> new NotFoundException("Abonnement introuvable."));
        LocalDate end = Objects.requireNonNullElse(form.getEndDate(), LocalDate.now(ABIDJAN));
        if (end.isBefore(subscription.getStartDate())) {
            throw new BadRequestException("La fin d'abonnement précède son début.");
        }
        subscription.setEndDate(end);
        this.subscriptionRepository.save(subscription);
        this.monthlyBilling.stopAfter(subscription.getStudentFee(), end);
    }

    /* ------------------------------------------------------------------ Chiffres ---- */

    public TransportDtos.Overview overview() {
        UUID scope = this.scope();
        LocalDate today = LocalDate.now(ABIDJAN);
        List<TransportDtos.Line> lines = this.lines().stream().filter(TransportDtos.Line::isActive).toList();
        long riders = lines.stream().mapToLong(TransportDtos.Line::getRiders).sum();
        long seats = lines.stream().mapToLong(TransportDtos.Line::getCapacity).sum();
        UUID year = this.academicYearContext.consultedId();
        long enrolled = Objects.isNull(year)
                ? this.studentRepository.findAllByEstablishment_Id(scope, PageRequest.of(0, 1)).getTotalElements()
                : this.enrollmentRepository.countByAcademicYear_IdAndStudent_Establishment_Id(year, scope);

        List<InstallmentEntity> overdue = this.subscriptionsOfYear(scope).stream()
                .map(TransportSubscriptionEntity::getStudentFee).filter(Objects::nonNull)
                .flatMap(fee -> this.installmentRepository.findByStudentFee_Id(fee.getId()).stream())
                .filter(i -> i.getStatus() == InstallmentStatus.PENDING && Objects.nonNull(i.getDueDate()) && i.getDueDate().isBefore(today))
                .toList();

        return TransportDtos.Overview.builder()
                .riders(riders)
                .shareOfEnrollment(enrolled == 0 ? 0 : (int) Math.round(riders * 100.0 / enrolled))
                .linesInService(lines.size())
                .arrivedToday(lines.stream().filter(l -> l.getStatus() == LineStatus.ARRIVED).count())
                .seats(seats)
                .seatsFree(Math.max(0, seats - riders))
                .fillRate(seats == 0 ? 0 : (int) Math.round(riders * 100.0 / seats))
                .unpaidAmount(overdue.stream().map(InstallmentEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .unpaidStudents(overdue.stream().map(i -> i.getStudentFee().getStudent().getId()).distinct().count())
                .alerts(lines.stream().filter(l -> l.getStatus() == LineStatus.DELAYED || l.getStatus() == LineStatus.INCIDENT).toList())
                .build();
    }

    /** Pour l'application des familles : la ligne de chaque enfant et où elle en est aujourd'hui. */
    public List<TransportDtos.ChildRide> mine() {
        UUID parentId = this.currentUserProvider.currentUser().getId();
        LocalDate today = LocalDate.now(ABIDJAN);
        List<TransportDtos.ChildRide> rides = new ArrayList<>();
        for (StudentEntity child : this.studentRepository.findByParentUsers_Id(parentId)) {
            this.subscriptionRepository.findByStudent_Id(child.getId()).stream().filter(s -> s.activeOn(today)).findFirst()
                    .ifPresent(s -> {
                        TransportLineEntity line = s.getLine();
                        TransportLineStatusEntity status = this.statusRepository.findByLine_IdAndDay(line.getId(), today).orElse(null);
                        rides.add(TransportDtos.ChildRide.builder()
                                .studentId(child.getId())
                                .studentName(String.format("%s %s", child.getLastName(), child.getFirstName()))
                                .lineName(line.getName())
                                .stopName(Objects.isNull(s.getStop()) ? null : s.getStop().getName())
                                .pickupTime(Objects.isNull(s.getStop()) ? null : s.getStop().getPickupTime())
                                .eveningDeparture(line.getEveningDeparture())
                                .driverName(Objects.isNull(line.getDriver()) ? null : fullName(line.getDriver()))
                                .driverPhone(Objects.isNull(line.getDriver()) ? null : line.getDriver().getPhone())
                                .status(Objects.isNull(status) ? LineStatus.NOT_STARTED : status.getStatus())
                                .delayMinutes(Objects.isNull(status) ? null : status.getDelayMinutes())
                                .build());
                    });
        }
        return rides;
    }

    /* ------------------------------------------------------------------ Outils ---- */

    private TransportDtos.Line toDto(TransportLineEntity line, List<TransportSubscriptionEntity> subscriptions,
                                     TransportLineStatusEntity status, LocalDate today) {
        List<TransportSubscriptionEntity> riders = subscriptions.stream()
                .filter(s -> s.getLine().getId().equals(line.getId()) && s.activeOn(today)).toList();
        List<TransportStopEntity> stops = this.stopRepository.findByLine_IdOrderByPositionAsc(line.getId());
        List<String> names = new ArrayList<>(stops.stream().map(TransportStopEntity::getName).toList());
        String itinerary = names.isEmpty() ? "—"
                : (names.size() <= 2 ? String.join(" → ", names) : names.getFirst() + " → " + names.get(names.size() / 2))
                + " → École";
        TransportDriverEntity driver = line.getDriver();
        return TransportDtos.Line.builder()
                .id(line.getId().toString())
                .name(line.getName())
                .itinerary(itinerary)
                .vehiclePlate(line.getVehiclePlate())
                .capacity(line.getCapacity())
                .driverId(Objects.isNull(driver) ? null : driver.getId().toString())
                .driverName(Objects.isNull(driver) ? null : fullName(driver))
                .driverPhone(Objects.isNull(driver) ? null : driver.getPhone())
                .morningDeparture(line.getMorningDeparture())
                .eveningDeparture(line.getEveningDeparture())
                .days(List.of(line.getDays().split(",")))
                .attendant(line.getAttendant())
                .startDate(line.getStartDate())
                .monthlyPrice(line.getMonthlyPrice())
                .active(line.isActive())
                .riders(riders.size())
                .stops(stops.stream().map(stop -> TransportDtos.Stop.builder()
                        .id(stop.getId().toString())
                        .position(stop.getPosition())
                        .name(stop.getName())
                        .pickupTime(stop.getPickupTime())
                        .latitude(stop.getLatitude())
                        .longitude(stop.getLongitude())
                        .riders(riders.stream().filter(r -> Objects.nonNull(r.getStop()) && r.getStop().getId().equals(stop.getId())).count())
                        .build()).toList())
                .status(Objects.isNull(status) ? LineStatus.NOT_STARTED : status.getStatus())
                .delayMinutes(Objects.isNull(status) ? null : status.getDelayMinutes())
                .note(Objects.isNull(status) ? null : status.getNote())
                .build();
    }

    private static TransportDtos.Driver toDto(TransportDriverEntity driver, List<String> lines) {
        boolean licenseValid = Objects.nonNull(driver.getLicenseExpiry()) && !driver.getLicenseExpiry().isBefore(LocalDate.now(ABIDJAN));
        List<String> documents = StringUtils.isBlank(driver.getDocuments()) ? List.of() : List.of(driver.getDocuments().split(","));
        return TransportDtos.Driver.builder()
                .id(driver.getId().toString())
                .firstName(driver.getFirstName())
                .lastName(driver.getLastName())
                .phone(driver.getPhone())
                .licenseCategory(driver.getLicenseCategory())
                .licenseExpiry(driver.getLicenseExpiry())
                .documents(documents)
                .active(driver.isActive())
                .dossierComplete(StringUtils.isNotBlank(driver.getPhone()) && licenseValid && documents.containsAll(DRIVER_DOCUMENTS))
                .lines(lines)
                .build();
    }

    private StudentPaymentStatus paymentStatus(StudentFeeEntity studentFee, LocalDate today) {
        if (Objects.isNull(studentFee)) {
            return StudentPaymentStatus.UP_TO_DATE;
        }
        List<InstallmentEntity> lines = this.installmentRepository.findByStudentFee_Id(studentFee.getId());
        if (lines.stream().anyMatch(i -> i.getStatus() == InstallmentStatus.PENDING && Objects.nonNull(i.getDueDate()) && i.getDueDate().isBefore(today))) {
            return StudentPaymentStatus.OVERDUE;
        }
        boolean paid = lines.stream().anyMatch(i -> i.getStatus() == InstallmentStatus.PAID);
        boolean pending = lines.stream().anyMatch(i -> i.getStatus() == InstallmentStatus.PENDING);
        return paid && pending ? StudentPaymentStatus.DEPOSIT : StudentPaymentStatus.UP_TO_DATE;
    }

    private Map<UUID, String> classNames(Collection<UUID> studentIds) {
        Map<UUID, String> names = new HashMap<>();
        UUID year = this.academicYearContext.consultedId();
        if (Objects.isNull(year) || studentIds.isEmpty()) {
            return names;
        }
        for (StudentEnrollmentEntity enrollment : this.enrollmentRepository.findByAcademicYear_IdAndStudent_IdIn(year, studentIds)) {
            if (Objects.nonNull(enrollment.getSchoolClass())) {
                names.put(enrollment.getStudent().getId(), enrollment.getSchoolClass().getName());
            }
        }
        return names;
    }

    private List<TransportLineEntity> linesOfYear(UUID scope) {
        return this.lineRepository.findByEstablishment_IdOrderByNameAsc(scope).stream()
                .filter(line -> this.academicYearContext.inConsultedYear(line.getAcademicYear())).toList();
    }

    private List<TransportSubscriptionEntity> subscriptionsOfYear(UUID scope) {
        return this.subscriptionRepository.findByStudent_Establishment_Id(scope).stream()
                .filter(s -> this.academicYearContext.inConsultedYear(s.getAcademicYear())).toList();
    }

    private void assertNameFree(UUID scope, String name, UUID except) {
        if (this.linesOfYear(scope).stream().anyMatch(l -> !l.getId().equals(except) && l.getName().equalsIgnoreCase(name.trim()))) {
            throw new BadRequestException(String.format("Une ligne nommée %s existe déjà.", name.trim()));
        }
    }

    /** Les trimestres de l'école pour l'année : sans eux, pas de facturation au trimestre. */
    private List<com.ypyit.neoelima.domain.establishment.entity.AcademicPeriodEntity> termsOf(UUID scope, AcademicYearEntity year) {
        if (Objects.isNull(year)) {
            throw new BadRequestException("Sans année scolaire, la facturation au trimestre n'est pas possible.");
        }
        List<com.ypyit.neoelima.domain.establishment.entity.AcademicPeriodEntity> periods = this.academicYearContext.schoolYear(scope, year).getPeriods();
        if (periods.isEmpty()) {
            throw new BadRequestException("Définissez les trimestres de l'année dans Paramètres pour facturer au trimestre.");
        }
        return periods;
    }

    private TransportLineEntity loadLine(UUID id, UUID scope) {
        return this.lineRepository.findById(id).filter(l -> l.getEstablishment().getId().equals(scope))
                .orElseThrow(() -> new NotFoundException("Ligne introuvable."));
    }

    private TransportDriverEntity loadDriver(UUID id, UUID scope) {
        return this.driverRepository.findById(id).filter(d -> d.getEstablishment().getId().equals(scope))
                .orElseThrow(() -> new NotFoundException("Chauffeur introuvable."));
    }

    private UUID scope() {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        return scope;
    }

    private static String fullName(TransportDriverEntity driver) {
        return String.format("%s %s", driver.getLastName(), driver.getFirstName());
    }

    private static String trim(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}
