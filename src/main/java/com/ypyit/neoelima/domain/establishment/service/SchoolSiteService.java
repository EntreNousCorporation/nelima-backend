package com.ypyit.neoelima.domain.establishment.service;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.SchoolSiteDtos;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QInstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QStaffEntity;
import com.ypyit.neoelima.domain.establishment.entity.QStudentEnrollmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolGroupEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolGroupRepository;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Groupes scolaires à plusieurs sites.
 *
 * <p>Chaque site est un établissement à part entière : ses élèves, ses classes, ses frais, sa caisse.
 * Les équipes d'un site ne voient que lui. La direction du groupe ({@code group_wide}) choisit le
 * site qu'elle affiche — tout l'espace école bascule alors sur lui, par le seul
 * {@link CurrentUserProvider} — et dispose d'une vue « Tous les sites », consolidée et en lecture.
 *
 * <p>Les groupes et les directions de groupe se déclarent depuis le back-office de la plateforme.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolSiteService {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final EstablishmentRepository establishmentRepository;
    private final SchoolGroupRepository groupRepository;
    private final UserRepository userRepository;
    private final AcademicYearContext academicYearContext;
    private final CurrentUserProvider currentUserProvider;
    private final JPAQueryFactory queryFactory;

    /* ------------------------------------------------------------------ Côté école ---- */

    /** Le site affiché et ceux que l'appelant peut afficher. */
    public SchoolSiteDtos.Sites sites() {
        EstablishmentUserEntity user = this.schoolUser();
        EstablishmentEntity home = user.getEstablishment();
        UUID current = this.currentUserProvider.currentEstablishmentId().orElse(home.getId());
        SchoolGroupEntity group = home.getSchoolGroup();
        List<EstablishmentEntity> sites = user.isGroupWide() && Objects.nonNull(group)
                ? this.establishmentRepository.findBySchoolGroup_IdOrderByNameAsc(group.getId())
                : List.of(home);
        return SchoolSiteDtos.Sites.builder()
                .groupName(Objects.isNull(group) ? null : group.getName())
                .groupWide(user.isGroupWide() && Objects.nonNull(group))
                .homeId(home.getId())
                .currentId(current)
                .sites(sites.stream().map(SchoolSiteService::siteOf).toList())
                .build();
    }

    /** Change le site affiché ; sans identifiant, retour au site de rattachement. */
    @Transactional
    public SchoolSiteDtos.Sites switchTo(UUID establishmentId) {
        EstablishmentUserEntity user = this.schoolUser();
        UUID home = user.getEstablishment().getId();
        if (Objects.isNull(establishmentId) || home.equals(establishmentId)) {
            user.setConsultedEstablishment(null);
        } else {
            if (!user.isGroupWide() || !this.establishmentRepository.sameGroup(home, establishmentId)) {
                throw new AccessDeniedException("Ce site n'est pas accessible depuis votre compte.");
            }
            user.setConsultedEstablishment(this.establishmentRepository.getReferenceById(establishmentId));
        }
        this.userRepository.saveAndFlush(user);
        return this.sites();
    }

    /**
     * « Tous les sites » : les chiffres de l'année consultée, site par site, pour la direction du
     * groupe. En lecture seule : on agit toujours dans un site.
     */
    public SchoolSiteDtos.Overview overview() {
        EstablishmentUserEntity user = this.schoolUser();
        SchoolGroupEntity group = user.getEstablishment().getSchoolGroup();
        if (!user.isGroupWide() || Objects.isNull(group)) {
            throw new AccessDeniedException("La vue « Tous les sites » est réservée à la direction du groupe.");
        }
        // Des montants de tous les sites : la comptabilité est exigée, comme pour ceux d'un site.
        this.currentUserProvider.assertPermission("accounting:read");
        List<EstablishmentEntity> sites = this.establishmentRepository.findBySchoolGroup_IdOrderByNameAsc(group.getId());
        List<UUID> ids = sites.stream().map(EstablishmentEntity::getId).toList();
        UUID year = this.academicYearContext.consultedId();
        LocalDate today = LocalDate.now(ABIDJAN);

        Map<UUID, Long> students = this.enrolled(ids, year);
        Map<UUID, Long> staff = this.activeStaff(ids);
        Map<UUID, BigDecimal> expected = this.amounts(ids, year, null, false);
        Map<UUID, BigDecimal> collected = this.amounts(ids, year, InstallmentStatus.PAID, false);
        Map<UUID, BigDecimal> overdue = this.amounts(ids, year, InstallmentStatus.PENDING, true);

        List<SchoolSiteDtos.SiteFigures> rows = new ArrayList<>();
        for (EstablishmentEntity site : sites) {
            BigDecimal due = expected.getOrDefault(site.getId(), BigDecimal.ZERO);
            BigDecimal paid = collected.getOrDefault(site.getId(), BigDecimal.ZERO);
            rows.add(SchoolSiteDtos.SiteFigures.builder()
                    .site(siteOf(site))
                    .students(students.getOrDefault(site.getId(), 0L))
                    .staff(staff.getOrDefault(site.getId(), 0L))
                    .expected(due)
                    .collected(paid)
                    .overdue(overdue.getOrDefault(site.getId(), BigDecimal.ZERO))
                    .collectionRate(due.signum() == 0 ? 0 : paid.multiply(BigDecimal.valueOf(100))
                            .divide(due, 0, java.math.RoundingMode.HALF_UP).intValue())
                    .build());
        }
        return SchoolSiteDtos.Overview.builder()
                .groupName(group.getName())
                .asOf(today)
                .sites(rows)
                .build();
    }

    /* ------------------------------------------------------------------ Plateforme ---- */

    public List<SchoolSiteDtos.Group> groups() {
        this.assertPlatform();
        return this.groupRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(SchoolGroupEntity::getName))
                .map(this::groupOf)
                .toList();
    }

    @Transactional
    public SchoolSiteDtos.Group saveGroup(UUID id, String name, Collection<UUID> establishmentIds) {
        this.assertPlatform();
        if (StringUtils.isBlank(name)) {
            throw new BadRequestException("Le groupe doit avoir un nom.");
        }
        SchoolGroupEntity group = Objects.isNull(id) ? new SchoolGroupEntity()
                : this.groupRepository.findById(id).orElseThrow(() -> new NotFoundException("Groupe introuvable."));
        boolean renamed = !name.trim().equalsIgnoreCase(Objects.toString(group.getName(), ""));
        if (renamed && this.groupRepository.existsByNameIgnoreCase(name.trim())) {
            throw new BadRequestException("Un groupe porte déjà ce nom.");
        }
        group.setName(name.trim());
        SchoolGroupEntity saved = this.groupRepository.saveAndFlush(group);

        Set<UUID> wanted = Set.copyOf(Objects.requireNonNullElse(establishmentIds, List.of()));
        // Les sites retirés redeviennent des écoles indépendantes.
        for (EstablishmentEntity site : this.establishmentRepository.findBySchoolGroup_IdOrderByNameAsc(saved.getId())) {
            if (!wanted.contains(site.getId())) {
                site.setSchoolGroup(null);
                this.establishmentRepository.save(site);
            }
        }
        for (UUID establishmentId : wanted) {
            EstablishmentEntity site = this.establishmentRepository.findById(establishmentId)
                    .orElseThrow(() -> new NotFoundException("Établissement introuvable."));
            if (Objects.nonNull(site.getSchoolGroup()) && !site.getSchoolGroup().getId().equals(saved.getId())) {
                throw new BadRequestException(String.format("%s fait déjà partie du groupe %s.",
                        site.getName(), site.getSchoolGroup().getName()));
            }
            site.setSchoolGroup(saved);
            this.establishmentRepository.save(site);
        }
        this.establishmentRepository.flush();
        log.info("SCHOOL_GROUP_SAVED: {} with {} site(s)", saved.getName(), wanted.size());
        return this.groupOf(saved);
    }

    @Transactional
    public void deleteGroup(UUID id) {
        this.assertPlatform();
        SchoolGroupEntity group = this.groupRepository.findById(id).orElseThrow(() -> new NotFoundException("Groupe introuvable."));
        for (EstablishmentEntity site : this.establishmentRepository.findBySchoolGroup_IdOrderByNameAsc(id)) {
            site.setSchoolGroup(null);
            this.establishmentRepository.save(site);
        }
        this.groupRepository.delete(group);
    }

    /** Fait d'un compte d'école une direction de groupe, ou l'en retire. */
    @Transactional
    public SchoolSiteDtos.Member setGroupWide(UUID userId, boolean groupWide) {
        this.assertPlatform();
        UserEntity user = this.userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Compte introuvable."));
        if (!(org.hibernate.Hibernate.unproxy(user) instanceof EstablishmentUserEntity schoolUser)
                || Objects.isNull(schoolUser.getEstablishment())
                || Objects.isNull(schoolUser.getEstablishment().getSchoolGroup())) {
            throw new BadRequestException("Seul un compte d'un site rattaché à un groupe peut être direction de groupe.");
        }
        if (groupWide && (Objects.isNull(schoolUser.getRole())
                || !Set.of("ESTABLISHMENT_ROOT", "DIRECTEUR").contains(schoolUser.getRole().getCode()))) {
            throw new BadRequestException("La direction de groupe se donne à un directeur ou à l'administrateur d'un site.");
        }
        schoolUser.setGroupWide(groupWide);
        if (!groupWide) {
            schoolUser.setConsultedEstablishment(null);
        }
        this.userRepository.saveAndFlush(schoolUser);
        return memberOf(schoolUser);
    }

    private SchoolSiteDtos.Group groupOf(SchoolGroupEntity group) {
        List<EstablishmentEntity> sites = this.establishmentRepository.findBySchoolGroup_IdOrderByNameAsc(group.getId());
        List<UUID> ids = sites.stream().map(EstablishmentEntity::getId).toList();
        List<SchoolSiteDtos.Member> members = ids.isEmpty() ? List.of()
                : this.userRepository.findSchoolUsers(ids).stream()
                        .map(SchoolSiteService::memberOf)
                        .sorted(java.util.Comparator.comparing(SchoolSiteDtos.Member::getSiteName)
                                .thenComparing(SchoolSiteDtos.Member::getName))
                        .toList();
        return SchoolSiteDtos.Group.builder()
                .id(group.getId())
                .name(group.getName())
                .sites(sites.stream().map(SchoolSiteService::siteOf).toList())
                .members(members)
                .build();
    }

    private static SchoolSiteDtos.Member memberOf(EstablishmentUserEntity user) {
        return SchoolSiteDtos.Member.builder()
                .id(user.getId())
                .name(String.format("%s %s", Objects.toString(user.getLastName(), ""), Objects.toString(user.getFirstName(), "")).trim())
                .roleCode(Objects.isNull(user.getRole()) ? null : user.getRole().getCode())
                .siteName(user.getEstablishment().getName())
                .groupWide(user.isGroupWide())
                .build();
    }

    private static SchoolSiteDtos.Site siteOf(EstablishmentEntity site) {
        return SchoolSiteDtos.Site.builder().id(site.getId()).name(site.getName()).city(site.getCity()).build();
    }

    /* ------------------------------------------------------------------ Chiffres ---- */

    private Map<UUID, Long> enrolled(List<UUID> sites, UUID year) {
        if (Objects.isNull(year)) {
            return Map.of();
        }
        QStudentEnrollmentEntity enrollment = QStudentEnrollmentEntity.studentEnrollmentEntity;
        return this.toMap(this.queryFactory.select(enrollment.student.establishment.id, enrollment.count())
                .from(enrollment)
                .where(enrollment.academicYear.id.eq(year), enrollment.student.establishment.id.in(sites))
                .groupBy(enrollment.student.establishment.id)
                .fetch(), t -> Objects.requireNonNullElse(t.get(1, Long.class), 0L));
    }

    private Map<UUID, Long> activeStaff(List<UUID> sites) {
        QStaffEntity staff = QStaffEntity.staffEntity;
        return this.toMap(this.queryFactory.select(staff.establishment.id, staff.count())
                .from(staff)
                .where(staff.active.isTrue(), staff.establishment.id.in(sites))
                .groupBy(staff.establishment.id)
                .fetch(), t -> Objects.requireNonNullElse(t.get(1, Long.class), 0L));
    }

    /**
     * Sommes des tranches de l'année par site. Sans statut : l'attendu (dû et réglé, hors annulé,
     * passé en perte ou reporté).
     */
    private Map<UUID, BigDecimal> amounts(List<UUID> sites, UUID year, InstallmentStatus status, boolean overdueOnly) {
        if (Objects.isNull(year)) {
            return Map.of();
        }
        QInstallmentEntity installment = QInstallmentEntity.installmentEntity;
        var site = installment.studentFee.student.establishment.id;
        return this.toMap(this.queryFactory.select(site, installment.amount.sum())
                .from(installment)
                .where(site.in(sites),
                        installment.studentFee.fee.academicYear.id.eq(year),
                        Objects.isNull(status)
                                ? installment.status.in(InstallmentStatus.PENDING, InstallmentStatus.PAID)
                                : installment.status.eq(status),
                        overdueOnly ? installment.dueDate.before(LocalDate.now(ABIDJAN)) : null)
                .groupBy(site)
                .fetch(), t -> Objects.requireNonNullElse(t.get(1, BigDecimal.class), BigDecimal.ZERO));
    }

    private <T> Map<UUID, T> toMap(List<Tuple> rows, Function<Tuple, T> value) {
        Map<UUID, T> map = new HashMap<>();
        rows.forEach(row -> map.put(row.get(0, UUID.class), value.apply(row)));
        return map;
    }

    private EstablishmentUserEntity schoolUser() {
        UserEntity user = this.currentUserProvider.currentUser();
        if (user instanceof EstablishmentUserEntity schoolUser && Objects.nonNull(schoolUser.getEstablishment())) {
            return schoolUser;
        }
        throw new AccessDeniedException("Réservé aux comptes d'établissement.");
    }

    private void assertPlatform() {
        if (!this.currentUserProvider.isPlatformAdmin()) {
            throw new AccessDeniedException("Réservé à l'équipe de la plateforme.");
        }
    }
}
