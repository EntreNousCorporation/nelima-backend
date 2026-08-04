package com.ypyit.neoelima.domain.payment;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
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
import com.ypyit.neoelima.domain.payment.service.BillingSettingsService;
import com.ypyit.neoelima.domain.payment.service.OnlinePaymentService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Taux de commission réglable depuis le back-office.
 *
 * <p>C'est le paramètre le plus sensible de la plateforme : il détermine ce que paient toutes les
 * familles de toutes les écoles. Deux exigences sont vérifiées ici — qu'un taux modifié s'applique
 * au paiement suivant et non au prochain redémarrage, et qu'une saisie manifestement erronée soit
 * refusée plutôt qu'appliquée.
 */
@Transactional
class BillingSettingsServiceTest extends AbstractIntegrationTest {

    @Autowired
    private BillingSettingsService billingSettingsService;
    @Autowired
    private OnlinePaymentService onlinePaymentService;
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
    private UserRepository userRepository;

    private InstallmentEntity installment;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        EstablishmentEntity school = establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École taux " + UUID.randomUUID()).active(true).build());
        StudentEntity student = studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Aaron").lastName("Koffi")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2012, 4, 3)).establishment(school).build());
        FeeEntity fee = feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID()).price(new BigDecimal("100000"))
                .establishment(school).build());
        StudentFeeEntity studentFee = studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .student(student).fee(fee).build());
        installment = installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("Versement").amount(new BigDecimal("100000"))
                .status(InstallmentStatus.PENDING).build());

        String email = "parent-" + UUID.randomUUID() + "@gmail.com";
        userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName("Jean").lastName("Kouassi").contacts(primaryEmail(email)).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }

    @Test
    @DisplayName("un taux modifié s'applique au devis suivant")
    void newRateAppliesImmediately() {
        // Le taux était figé dans la configuration : le changer imposait un redéploiement, et le
        // devis affiché serait resté sur l'ancienne valeur jusque-là.
        billingSettingsService.updateRate(new BigDecimal("0.05"));

        var quote = onlinePaymentService.quote(installment.getId());

        assertThat(quote.getCommissionRate()).isEqualByComparingTo(new BigDecimal("0.05"));
        assertThat(quote.getAmountCommission()).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(quote.getTotalAmount()).isEqualByComparingTo(new BigDecimal("105000"));
    }

    @Test
    @DisplayName("un pourcentage saisi à la place d'une fraction est refusé")
    void rejectsAPercentageMistakenForAFraction() {
        // Saisir 2 au lieu de 0,02 débiterait deux cents pour cent du montant de la scolarité.
        assertThatThrownBy(() -> billingSettingsService.updateRate(new BigDecimal("2")))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> billingSettingsService.updateRate(new BigDecimal("-0.01")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("sans paramètre enregistré, le taux de configuration s'applique")
    void fallsBackToConfiguredRate() {
        // Aucune écriture préalable : c'est l'état d'une plateforme fraîchement déployée.
        assertThat(billingSettingsService.currentRate()).isEqualByComparingTo(new BigDecimal("0.02"));
    }

    @Test
    @DisplayName("un taux nul est accepté : une école peut être exonérée")
    void allowsZero() {
        billingSettingsService.updateRate(BigDecimal.ZERO);

        var quote = onlinePaymentService.quote(installment.getId());

        assertThat(quote.getAmountCommission()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(quote.getTotalAmount()).isEqualByComparingTo(new BigDecimal("100000"));
    }

    private static Set<ContactEntity> primaryEmail(String email) {
        return new HashSet<>(List.of(ContactEntity.builder()
                .type(ContactType.EMAIL).value(email).isPrimary(true).build()));
    }
}
