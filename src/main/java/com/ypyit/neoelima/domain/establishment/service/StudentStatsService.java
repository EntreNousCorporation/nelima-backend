package com.ypyit.neoelima.domain.establishment.service;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.StudentStatsDto;
import com.ypyit.neoelima.domain.establishment.entity.QInstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QLevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.entity.QSchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.QStudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.EducationCycle;
import com.ypyit.neoelima.domain.establishment.enums.Gender;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.enums.StudentPaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Chiffres de l'écran Élèves, sur tout l'effectif de l'école.
 *
 * <p>Une dizaine de comptages en base, aucun élève chargé : une école de deux mille élèves n'a pas
 * à les faire transiter pour en compter les filles.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentStatsService {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private final JPAQueryFactory queryFactory;
    private final CurrentUserProvider currentUserProvider;

    public StudentStatsDto compute() {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        QStudentEntity student = QStudentEntity.studentEntity;
        BooleanExpression ofSchool = student.establishment.id.eq(scope);
        LocalDate today = LocalDate.now(ABIDJAN);

        long total = this.count(ofSchool);
        BooleanExpression incomplete = StudentFilters.incompleteDossier(student);
        BooleanExpression overdue = StudentFilters.overdue(student, today);
        BooleanExpression deposit = StudentFilters.paymentStatus(student, StudentPaymentStatus.DEPOSIT, today);

        long incompleteCount = this.count(ofSchool.and(incomplete));
        long overdueCount = this.count(ofSchool.and(overdue));
        long statusOverdue = this.count(ofSchool.and(overdue).and(incomplete.not()));
        long statusDeposit = this.count(ofSchool.and(deposit).and(incomplete.not()));

        Map<Gender, Long> genders = new HashMap<>();
        this.queryFactory.select(student.gender, student.count()).from(student)
                .where(ofSchool).groupBy(student.gender).fetch()
                .forEach(row -> genders.put(row.get(student.gender), row.get(student.count())));

        QLevelOfStudyEntity level = QLevelOfStudyEntity.levelOfStudyEntity;
        Map<EducationCycle, Long> cycles = new HashMap<>();
        this.queryFactory.select(level.cycle, student.count()).from(student)
                .leftJoin(student.levelOfStudy, level)
                .where(ofSchool).groupBy(level.cycle).fetch()
                .forEach(row -> cycles.merge(row.get(level.cycle), row.get(student.count()), Long::sum));
        List<StudentStatsDto.CycleCount> byCycle = new ArrayList<>();
        Arrays.stream(EducationCycle.values()).forEach(cycle -> byCycle.add(StudentStatsDto.CycleCount.builder()
                .cycle(cycle).count(cycles.getOrDefault(cycle, 0L)).build()));
        if (cycles.getOrDefault(null, 0L) > 0) {
            byCycle.add(StudentStatsDto.CycleCount.builder().cycle(null).count(cycles.get(null)).build());
        }

        QInstallmentEntity installment = QInstallmentEntity.installmentEntity;
        BigDecimal overdueAmount = this.queryFactory.select(installment.amount.sum()).from(installment)
                .where(installment.studentFee.student.establishment.id.eq(scope),
                        installment.status.eq(InstallmentStatus.PENDING),
                        installment.dueDate.lt(today))
                .fetchOne();

        QSchoolClassEntity schoolClass = QSchoolClassEntity.schoolClassEntity;
        Long classCount = this.queryFactory.select(schoolClass.count()).from(schoolClass)
                .where(schoolClass.establishment.id.eq(scope)).fetchOne();

        return StudentStatsDto.builder()
                .total(total)
                .newThisMonth(this.count(ofSchool.and(student.createdAt.goe(
                        today.withDayOfMonth(1).atStartOfDay(ABIDJAN).toInstant()))))
                .incompleteDossier(incompleteCount)
                .withoutClass(this.count(ofSchool.and(student.schoolClass.isNull())))
                .overdueCount(overdueCount)
                .overdueAmount(Objects.requireNonNullElse(overdueAmount, BigDecimal.ZERO))
                .classCount(Objects.requireNonNullElse(classCount, 0L))
                .byCycle(byCycle)
                .female(genders.getOrDefault(Gender.FEMALE, 0L))
                .male(genders.getOrDefault(Gender.MALE, 0L))
                .genderUnknown(genders.getOrDefault(null, 0L))
                .statusIncomplete(incompleteCount)
                .statusOverdue(statusOverdue)
                .statusDeposit(statusDeposit)
                .statusUpToDate(total - incompleteCount - statusOverdue - statusDeposit)
                .build();
    }

    private long count(BooleanExpression where) {
        QStudentEntity student = QStudentEntity.studentEntity;
        Long count = this.queryFactory.select(student.count()).from(student).where(where).fetchOne();
        return Objects.requireNonNullElse(count, 0L);
    }
}
