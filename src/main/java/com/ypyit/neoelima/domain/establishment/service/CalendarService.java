package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.CalendarEntryDto;
import com.ypyit.neoelima.domain.establishment.dto.SchoolClassLiteDto;
import com.ypyit.neoelima.domain.establishment.entity.AcademicPeriodEntity;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolEventEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.CalendarEntryKind;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.enums.SchoolEventKind;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolEventRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Calendrier de l'établissement : la vie scolaire et l'argent sur la même grille.
 *
 * <p>Les échéances financières ne sont <strong>pas stockées</strong>. Elles sont déduites des
 * tranches réellement dues, groupées par frais et par date. Les saisir à la main créerait une
 * seconde vérité à côté de la dette, et rien ne garantirait qu'elles restent d'accord — une école
 * qui décale une échéance corrigerait un calendrier pendant que les familles resteraient
 * redevables à l'ancienne date.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarService {

    /** Permission qui commande la présence des montants dans la réponse. */
    public static final String READ_AMOUNTS = "fee:read";

    private final SchoolEventRepository schoolEventRepository;
    private final InstallmentRepository installmentRepository;
    private final StudentRepository studentRepository;
    private final AcademicYearService academicYearService;
    private final CurrentUserProvider currentUserProvider;

    /** Le calendrier de l'école : événements saisis, échéances agrégées et bornes de l'année. */
    public List<CalendarEntryDto> findAll(LocalDate from, LocalDate to) {
        UUID scope = this.scope();
        assertRange(from, to);

        List<CalendarEntryDto> entries = new ArrayList<>(
                this.schoolEventRepository
                        .findByEstablishment_IdAndDateBetweenOrderByDateAsc(scope, from, to).stream()
                        .map(CalendarService::toEntry).toList());

        entries.addAll(this.feeDueEntries(this.installmentRepository
                .findByStudentFee_Student_Establishment_IdAndDueDateBetween(scope, from, to)));

        entries.addAll(this.academicEntries(scope, from, to));

        return sorted(entries);
    }

    /**
     * Le calendrier d'une famille.
     *
     * <p>Deux différences avec celui de l'école, et elles comptent : seuls les événements rendus
     * visibles y figurent, et les échéances sont celles de <em>ses</em> enfants — le montant dû par
     * l'enfant, non l'agrégat de l'établissement, qui ne le regarde pas.
     */
    public List<CalendarEntryDto> findMine(LocalDate from, LocalDate to) {
        assertRange(from, to);
        UUID parentId = this.currentUserProvider.currentUser().getId();
        List<StudentEntity> children = this.studentRepository.findByParentUsers_Id(parentId);
        if (children.isEmpty()) {
            return List.of();
        }

        // Les écoles sont gardées entières et non réduites à leur identifiant : le fil parent est
        // mêlé quand les enfants sont dans deux établissements, et chaque entrée doit dire d'où
        // elle vient — un « Fermeture exceptionnelle » sans école ne veut rien dire.
        Map<UUID, EstablishmentEntity> establishments = children.stream()
                .map(StudentEntity::getEstablishment)
                .collect(Collectors.toMap(EstablishmentEntity::getId, school -> school,
                        (first, second) -> first, LinkedHashMap::new));
        Set<UUID> childClasses = children.stream()
                .map(StudentEntity::getSchoolClass)
                .filter(Objects::nonNull)
                .map(SchoolClassEntity::getId)
                .collect(Collectors.toSet());

        List<CalendarEntryDto> entries = new ArrayList<>();
        for (EstablishmentEntity school : establishments.values()) {
            UUID establishmentId = school.getId();
            this.schoolEventRepository
                    .findByEstablishment_IdAndDateBetweenOrderByDateAsc(establishmentId, from, to).stream()
                    .filter(SchoolEventEntity::isVisibleToFamilies)
                    .filter(event -> concerns(event, childClasses))
                    .map(CalendarService::toEntry)
                    .forEach(entries::add);
            // Les bornes de l'année sont servies aux familles sans réglage de visibilité : une
            // rentrée et un début de trimestre n'ont rien de confidentiel, et c'est précisément ce
            // qu'un parent cherche à savoir.
            // Les bornes de l'année sont bâties depuis l'année scolaire, qui ne porte pas l'école :
            // on les timbre ici, à l'endroit où on la connaît.
            entries.addAll(stamped(this.academicEntries(establishmentId, from, to), school));
        }

        List<InstallmentEntity> installments = children.stream()
                .flatMap(child -> this.installmentRepository
                        .findByStudentFee_Student_IdAndDueDateBetween(child.getId(), from, to).stream())
                .toList();
        entries.addAll(this.feeDueEntries(installments));

        return sorted(entries);
    }

    /**
     * Recopie l'école sur des entrées qui ne la portent pas.
     *
     * <p>Les bornes de l'année sont bâties depuis l'année scolaire, qui ne connaît pas
     * l'établissement. Plutôt que de faire descendre celui-ci dans le constructeur, on le timbre
     * là où il est connu : une seule ligne, au lieu d'un paramètre traversant trois méthodes.
     */
    private static List<CalendarEntryDto> stamped(List<CalendarEntryDto> entries,
                                                  EstablishmentEntity school) {
        entries.forEach(entry -> {
            entry.setEstablishmentId(school.getId().toString());
            entry.setEstablishmentName(school.getName());
        });
        return entries;
    }

    /**
     * Regroupe les tranches en échéances : une entrée par frais et par date.
     *
     * <p>Le groupement se fait sur la date portée par la tranche, et non sur celle du gabarit :
     * c'est la tranche que la famille doit, et c'est elle que le rappel automatique regarde.
     */
    private List<CalendarEntryDto> feeDueEntries(List<InstallmentEntity> installments) {
        boolean amountsVisible = this.currentUserProvider.hasPermission(READ_AMOUNTS);

        Map<String, List<InstallmentEntity>> grouped = new LinkedHashMap<>();
        for (InstallmentEntity installment : installments) {
            if (InstallmentStatus.CANCELLED.equals(installment.getStatus())) {
                // Une tranche annulée n'est plus due : la compter gonflerait l'attendu d'une somme
                // que personne ne réclame.
                continue;
            }
            FeeEntity fee = installment.getStudentFee().getFee();
            if (Objects.isNull(fee) || Objects.isNull(installment.getDueDate())) {
                continue;
            }
            grouped.computeIfAbsent(fee.getId() + "@" + installment.getDueDate(),
                    key -> new ArrayList<>()).add(installment);
        }

        List<CalendarEntryDto> entries = new ArrayList<>();
        for (Map.Entry<String, List<InstallmentEntity>> group : grouped.entrySet()) {
            List<InstallmentEntity> lines = group.getValue();
            InstallmentEntity first = lines.getFirst();
            FeeEntity fee = first.getStudentFee().getFee();

            long settled = lines.stream()
                    .filter(line -> InstallmentStatus.PAID.equals(line.getStatus())).count();

            entries.add(CalendarEntryDto.builder()
                    .id(group.getKey())
                    .kind(CalendarEntryKind.FEE_DUE)
                    .title(fee.getName())
                    .date(first.getDueDate())
                    .allDay(true)
                    .details(first.getLabel())
                    .scope(String.format("%d élève(s) concerné(s)", lines.size()))
                    // L'école se lit sur l'élève : une échéance appartient à l'établissement où
                    // l'enfant est inscrit, jamais à celui du frais pris isolément.
                    .establishmentId(Objects.toString(
                            fee.getEstablishment().getId(), null))
                    .establishmentName(fee.getEstablishment().getName())
                    .amountExpected(amountsVisible ? sum(lines, false) : null)
                    .amountCollected(amountsVisible ? sum(lines, true) : null)
                    .studentsConcerned((long) lines.size())
                    .studentsSettled(settled)
                    .build());
        }
        return entries;
    }

    /**
     * Les repères de l'année scolaire, déduits de l'année active.
     *
     * <p>Ce sont les <strong>bornes</strong> qui sont posées, non la durée : une période longue de
     * trois mois occuperait toute la grille sans rien apprendre, alors qu'une direction cherche à
     * savoir quand un trimestre commence et quand il finit.
     *
     * <p>Faute de périodes déclarées, ce sont les bornes de l'année elle-même qui paraissent :
     * une école qui n'a saisi que « 2025-2026 » y voit tout de même sa rentrée.
     */
    private List<CalendarEntryDto> academicEntries(UUID establishmentId, LocalDate from, LocalDate to) {
        return this.academicYearService.activeYear(establishmentId)
                .map(year -> {
                    List<CalendarEntryDto> entries = new ArrayList<>();
                    if (year.getPeriods().isEmpty()) {
                        addMilestone(entries, "Rentrée scolaire — " + year.getLabel(),
                                year.getStartDate(), from, to);
                        addMilestone(entries, "Fin de l'année — " + year.getLabel(),
                                year.getEndDate(), from, to);
                        return entries;
                    }
                    for (AcademicPeriodEntity period : year.getPeriods()) {
                        addMilestone(entries, "Début — " + period.getLabel(),
                                period.getStartDate(), from, to);
                        addMilestone(entries, "Fin — " + period.getLabel(),
                                period.getEndDate(), from, to);
                    }
                    return entries;
                })
                .orElseGet(List::of);
    }

    private static void addMilestone(List<CalendarEntryDto> entries, String title,
                                     LocalDate date, LocalDate from, LocalDate to) {
        if (date.isBefore(from) || date.isAfter(to)) {
            return;
        }
        entries.add(CalendarEntryDto.builder()
                .id("period@" + date + "@" + title)
                .kind(CalendarEntryKind.ACADEMIC_PERIOD)
                .title(title)
                .date(date)
                .allDay(true)
                .wholeSchool(true)
                .scope("Tout l'établissement")
                .build());
    }

    private static BigDecimal sum(List<InstallmentEntity> lines, boolean paidOnly) {
        return lines.stream()
                .filter(line -> !paidOnly || InstallmentStatus.PAID.equals(line.getStatus()))
                .map(InstallmentEntity::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Concerne l'un de ces enfants : tout l'établissement, ou l'une de leurs classes. */
    /**
     * Vrai quand l'événement concerne au moins une des classes de la famille.
     *
     * <p>Ouvert au paquet plutôt que privé : le fil de notifications pose la même question et la
     * recopier serait s'engager à corriger le ciblage à deux endroits.
     */
    static boolean concerns(SchoolEventEntity event, Set<UUID> childClasses) {
        if (event.isWholeSchool()) {
            return true;
        }
        return event.getClasses().stream()
                .anyMatch(schoolClass -> childClasses.contains(schoolClass.getId()));
    }

    static CalendarEntryDto toEntry(SchoolEventEntity entity) {
        return CalendarEntryDto.builder()
                .id(entity.getId().toString())
                .kind(SchoolEventKind.EXAM.equals(entity.getKind())
                        ? CalendarEntryKind.EXAM : CalendarEntryKind.SCHOOL_LIFE)
                .title(entity.getTitle())
                .date(entity.getDate())
                .allDay(entity.isAllDay())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .details(entity.getDetails())
                .wholeSchool(entity.isWholeSchool())
                .scope(entity.isWholeSchool() ? "Tout l'établissement"
                        : entity.getClasses().stream()
                                .map(SchoolClassEntity::getName)
                                .sorted()
                                .collect(Collectors.joining(", ")))
                .classes(entity.getClasses().stream()
                        .sorted(Comparator.comparing(SchoolClassEntity::getName))
                        .map(schoolClass -> SchoolClassLiteDto.builder()
                                .id(schoolClass.getId().toString())
                                .name(schoolClass.getName())
                                .room(schoolClass.getRoom())
                                .build())
                        .toList())
                .visibleToFamilies(entity.isVisibleToFamilies())
                .lastNotifiedAt(entity.getLastNotifiedAt())
                .establishmentId(Objects.toString(entity.getEstablishment().getId(), null))
                .establishmentName(entity.getEstablishment().getName())
                .build();
    }

    /**
     * Une fenêtre bornée, et bornée court.
     *
     * <p>L'écran demande un mois. Sans borne, un appel sur dix ans agrégerait toutes les tranches
     * de l'établissement pour rendre une grille que personne ne regarde.
     */
    private static void assertRange(LocalDate from, LocalDate to) {
        if (Objects.isNull(from) || Objects.isNull(to) || to.isBefore(from)) {
            throw new BadRequestException("La période demandée est invalide.");
        }
        if (from.plusYears(1).isBefore(to)) {
            throw new BadRequestException("La période demandée ne peut pas dépasser un an.");
        }
    }

    private static List<CalendarEntryDto> sorted(List<CalendarEntryDto> entries) {
        return entries.stream()
                .sorted(Comparator.comparing(CalendarEntryDto::getDate)
                        .thenComparing(entry -> Objects.requireNonNullElse(
                                entry.getStartTime(), java.time.LocalTime.MIN))
                        .thenComparing(CalendarEntryDto::getTitle))
                .toList();
    }

    private UUID scope() {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        return scope;
    }
}
