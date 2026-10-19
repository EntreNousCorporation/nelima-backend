package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;

/**
 * Facturation au mois, pour les services auxquels on s'abonne : cantine, transport.
 *
 * <p>Une tranche par mois, du mois d'arrivée à la fin de l'année scolaire, due le 5 — ou le jour
 * d'arrivée s'il est plus tard. Arrêter l'abonnement annule les mois qui suivent celui du départ,
 * s'ils ne sont pas réglés ; le mois commencé reste dû.
 */
@Component
@RequiredArgsConstructor
public class MonthlyBilling {

    private static final int DUE_DAY = 5;

    private final InstallmentRepository installmentRepository;

    @Transactional
    public void bill(StudentFeeEntity studentFee, String label, BigDecimal monthlyPrice, LocalDate start,
                     AcademicYearEntity year) {
        LocalDate end = Objects.nonNull(year) ? year.getEndDate() : start.plusMonths(9);
        YearMonth month = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        while (!month.isAfter(last)) {
            LocalDate due = month.atDay(Math.min(DUE_DAY, month.lengthOfMonth()));
            if (due.isBefore(start)) {
                due = start;
            }
            this.installmentRepository.save(InstallmentEntity.builder()
                    .studentFee(studentFee)
                    .label(String.format("%s — %s %d", label,
                            month.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH), month.getYear()))
                    .amount(monthlyPrice)
                    .dueDate(due)
                    .status(InstallmentStatus.PENDING)
                    .build());
            month = month.plusMonths(1);
        }
    }

    /**
     * Facture au trimestre : une tranche par trimestre de l'école, au tarif mensuel multiplié par
     * les mois qu'il couvre à partir de l'arrivée. Payable au début du trimestre, ou le jour de
     * l'arrivée en cours de trimestre. Les trimestres déjà terminés à l'arrivée ne sont pas dus.
     */
    @Transactional
    public void billByTerm(StudentFeeEntity studentFee, String label, BigDecimal monthlyPrice, LocalDate start,
                           java.util.List<com.ypyit.neoelima.domain.establishment.entity.AcademicPeriodEntity> periods) {
        periods.stream()
                .sorted(java.util.Comparator.comparing(com.ypyit.neoelima.domain.establishment.entity.AcademicPeriodEntity::getStartDate))
                .filter(period -> !period.getEndDate().isBefore(start))
                .forEach(period -> {
                    LocalDate from = period.getStartDate().isBefore(start) ? start : period.getStartDate();
                    long months = java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(period.getEndDate())) + 1;
                    this.installmentRepository.save(InstallmentEntity.builder()
                            .studentFee(studentFee)
                            .label(String.format("%s — %s", label, period.getLabel()))
                            .amount(monthlyPrice.multiply(BigDecimal.valueOf(months)))
                            .dueDate(from)
                            .status(InstallmentStatus.PENDING)
                            .build());
                });
    }

    @Transactional
    public void stopAfter(StudentFeeEntity studentFee, LocalDate lastDay) {
        if (Objects.isNull(studentFee)) {
            return;
        }
        YearMonth lastMonth = YearMonth.from(lastDay);
        for (InstallmentEntity installment : this.installmentRepository.findByStudentFee_Id(studentFee.getId())) {
            if (installment.getStatus() == InstallmentStatus.PENDING && Objects.nonNull(installment.getDueDate())
                    && YearMonth.from(installment.getDueDate()).isAfter(lastMonth)) {
                installment.setStatus(InstallmentStatus.CANCELLED);
                this.installmentRepository.save(installment);
            }
        }
    }
}
