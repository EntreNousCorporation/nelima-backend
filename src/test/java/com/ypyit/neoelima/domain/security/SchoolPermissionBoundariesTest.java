package com.ypyit.neoelima.domain.security;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.domain.establishment.dto.DashboardSummaryDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.form.InstallmentSearchForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.service.DashboardService;
import com.ypyit.neoelima.domain.establishment.service.InstallmentService;
import com.ypyit.neoelima.domain.payment.service.ReceiptService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ce qu'un profil d'école lit selon ses permissions : montants, reçus, tranches. */
@Transactional
class SchoolPermissionBoundariesTest extends AbstractIntegrationTest {

    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private ReceiptService receiptService;
    @Autowired
    private InstallmentService installmentService;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private UserRepository userRepository;

    private EstablishmentEntity school;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.school = this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
    }

    @Test
    @DisplayName("sans la comptabilité, le tableau de bord ne montre ni encaissements ni impayés")
    void dashboardWithoutMoney() {
        this.login("student:read");
        DashboardSummaryDto summary = this.dashboardService.summaryOf(null);
        assertThat(summary.getCollectedThisMonth()).isNull();
        assertThat(summary.getPendingAmount()).isNull();
        assertThat(summary.getRecentReceipts()).isEmpty();
        assertThat(summary.getTopOverdue()).isEmpty();

        this.login("accounting:read", "fee:read");
        DashboardSummaryDto full = this.dashboardService.summaryOf(null);
        assertThat(full.getCollectedThisMonth()).isNotNull();
        assertThat(full.getPendingAmount()).isNotNull();
    }

    @Test
    @DisplayName("le journal des reçus et les tranches demandent la comptabilité ou les frais ; le guichet ne relit qu'un élève")
    void receiptsAndInstallments() {
        this.login("student:read");
        assertThatThrownBy(() -> this.receiptService.search(null, null, null, null, null, null, PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> this.installmentService.findAll(new InstallmentSearchForm(), PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);

        this.login("collection:write");
        assertThatThrownBy(() -> this.receiptService.search(null, null, null, null, null, null, PageRequest.of(0, 10)))
                .as("pas tout le journal").isInstanceOf(AccessDeniedException.class);
        assertThatCode(() -> this.receiptService.search(null, null, null, null, null, UUID.randomUUID(), PageRequest.of(0, 10)))
                .as("les reçus d'un élève, au guichet").doesNotThrowAnyException();
        assertThatCode(() -> this.installmentService.findAll(new InstallmentSearchForm(), PageRequest.of(0, 10)))
                .doesNotThrowAnyException();

        this.login("accounting:read");
        assertThatCode(() -> this.receiptService.search(null, null, null, null, null, null, PageRequest.of(0, 10)))
                .doesNotThrowAnyException();
    }

    private void login(String... permissions) {
        String email = "u-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(EstablishmentUserEntity.builder().role(roleWith(permissions))
                .firstName("Awa").lastName("Traoré").establishment(this.school)
                .contacts(new HashSet<>(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                .build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
