package com.ypyit.neoelima.domain.establishment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.domain.establishment.dto.PlatformOverviewDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentFeeEntity;
import com.ypyit.neoelima.domain.establishment.enums.InstallmentStatus;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentFeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.PlatformOverviewService;
import com.ypyit.neoelima.domain.payment.entity.PaymentIntentEntity;
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import com.ypyit.neoelima.domain.payment.repository.PaymentIntentRepository;
import com.ypyit.neoelima.domain.payment.repository.ReceiptRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Console du parc.
 *
 * <p>Ces chiffres sont ceux que YPYit lit pour savoir ce que la plateforme rapporte. Deux erreurs
 * seraient invisibles à l'œil : compter la commission d'une école sur une autre, et compter
 * l'encaissement du mois dernier sur celui-ci.
 */
@Transactional
class PlatformOverviewServiceTest extends AbstractIntegrationTest {

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    @Autowired
    private PlatformOverviewService platformOverviewService;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private FeeRepository feeRepository;
    @Autowired
    private StudentFeeRepository studentFeeRepository;
    @Autowired
    private InstallmentRepository installmentRepository;
    @Autowired
    private PaymentIntentRepository paymentIntentRepository;
    @Autowired
    private ReceiptRepository receiptRepository;

    @Test
    @DisplayName("la commission est rattachée à l'école de l'élève")
    void commissionIsAttributedToTheRightSchool() {
        EstablishmentEntity first = this.school("Alpha");
        EstablishmentEntity second = this.school("Beta");
        this.settledOnlinePayment(first, new BigDecimal("50000"), new BigDecimal("1000"), Instant.now());
        this.settledOnlinePayment(second, new BigDecimal("30000"), new BigDecimal("600"), Instant.now());

        PlatformOverviewDto overview = this.platformOverviewService.overview();

        assertThat(rowOf(overview, first).getCommissionThisMonth())
                .isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(rowOf(overview, second).getCommissionThisMonth())
                .isEqualByComparingTo(new BigDecimal("600"));
    }

    @Test
    @DisplayName("un paiement du mois précédent ne compte pas sur le mois courant")
    void previousMonthIsNotCountedTwice() {
        EstablishmentEntity school = this.school("Gamma");
        Instant lastMonth = LocalDate.now(ABIDJAN).withDayOfMonth(1).minusDays(3)
                .atStartOfDay(ABIDJAN).toInstant();
        this.settledOnlinePayment(school, new BigDecimal("40000"), new BigDecimal("800"), lastMonth);

        PlatformOverviewDto overview = this.platformOverviewService.overview();

        assertThat(rowOf(overview, school).getCommissionThisMonth()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(overview.getCommissionPreviousMonth()).isGreaterThanOrEqualTo(new BigDecimal("800"));
    }

    @Test
    @DisplayName("les impayés du parc sont ventilés par école")
    void overdueIsBrokenDownBySchool() {
        EstablishmentEntity school = this.school("Delta");
        StudentEntity student = this.student(school);
        this.installment(student, new BigDecimal("25000"),
                LocalDate.now(ABIDJAN).minusDays(10), InstallmentStatus.PENDING);
        // Échéance à venir : due, mais pas en retard. La confondre avec un impayé gonflerait le
        // chiffre que YPYit regarde pour juger de la santé d'une école.
        this.installment(student, new BigDecimal("15000"),
                LocalDate.now(ABIDJAN).plusMonths(1), InstallmentStatus.PENDING);

        PlatformOverviewDto overview = this.platformOverviewService.overview();

        PlatformOverviewDto.SchoolRowDto row = rowOf(overview, school);
        assertThat(row.getOverdueAmount()).isEqualByComparingTo(new BigDecimal("25000"));
        assertThat(row.getOverdueCount()).isEqualTo(1);
        assertThat(row.getStudentCount()).isEqualTo(1);
    }

    private static PlatformOverviewDto.SchoolRowDto rowOf(PlatformOverviewDto overview,
                                                          EstablishmentEntity school) {
        return overview.getSchools().stream()
                .filter(row -> row.getId().equals(school.getId().toString()))
                .findFirst().orElseThrow();
    }

    private EstablishmentEntity school(String prefix) {
        return this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name(prefix + " " + UUID.randomUUID()).active(true).isPrimary(true).build());
    }

    private StudentEntity student(EstablishmentEntity school) {
        return this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Aya").lastName("Traoré")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2013, 5, 12)).establishment(school).build());
    }

    private InstallmentEntity installment(StudentEntity student, BigDecimal amount,
                                          LocalDate dueDate, InstallmentStatus status) {
        FeeEntity fee = this.feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID()).price(amount)
                .establishment(student.getEstablishment()).build());
        StudentFeeEntity studentFee = this.studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .student(student).fee(fee).build());
        return this.installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("Versement").amount(amount)
                .dueDate(dueDate).status(status).build());
    }

    private void settledOnlinePayment(EstablishmentEntity school, BigDecimal amount,
                                      BigDecimal commission, Instant settledAt) {
        StudentEntity student = this.student(school);
        InstallmentEntity installment = this.installment(student, amount,
                LocalDate.now(ABIDJAN), InstallmentStatus.PAID);

        PaymentIntentEntity intent = this.paymentIntentRepository.saveAndFlush(PaymentIntentEntity.builder()
                .installment(installment)
                .internalReference(UUID.randomUUID().toString())
                .amountSchool(amount)
                .amountCommission(commission)
                .status(PaymentIntentStatus.SUCCEEDED)
                .channel(PaymentChannel.ONLINE)
                .settledAt(settledAt)
                .build());

        this.receiptRepository.saveAndFlush(ReceiptEntity.builder()
                .establishment(school)
                .paymentIntent(intent)
                .sequenceNumber(System.nanoTime())
                .number(UUID.randomUUID().toString().substring(0, 12))
                .amount(amount.add(commission))
                .issuedAt(settledAt)
                .channel(PaymentChannel.ONLINE)
                .studentLabel("Aya Traoré")
                .studentRegistrationNumber(student.getRegistrationNumber())
                .build());
    }
}
