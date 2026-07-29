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
import com.ypyit.neoelima.domain.payment.entity.ReceiptEntity;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import com.ypyit.neoelima.domain.payment.enums.PaymentIntentStatus;
import com.ypyit.neoelima.domain.payment.form.OfflineCollectionForm;
import com.ypyit.neoelima.domain.payment.repository.PaymentIntentRepository;
import com.ypyit.neoelima.domain.payment.service.OfflineCollectionService;
import com.ypyit.neoelima.domain.payment.service.ReceiptPdfRenderer;
import com.ypyit.neoelima.domain.payment.service.ReceiptService;
import org.springframework.data.domain.PageRequest;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chemin argent d'un encaissement au guichet : la tranche est soldée, la tentative de paiement
 * tracée et le reçu numéroté émis, le tout dans la même transaction.
 */
@Transactional
class OfflineCollectionServiceTest extends AbstractIntegrationTest {

    @Autowired
    private OfflineCollectionService offlineCollectionService;
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
    private UserRepository userRepository;
    @Autowired
    private ReceiptService receiptService;
    @Autowired
    private ReceiptPdfRenderer receiptPdfRenderer;

    private EstablishmentEntity establishment;
    private InstallmentEntity installment;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        establishment = establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École guichet " + UUID.randomUUID()).active(true).build());
        installment = anInstallmentOf(establishment, new BigDecimal("50000"));
        authenticateAsSchoolUserOf(establishment);
    }

    @Test
    @DisplayName("un encaissement en espèces solde la tranche et émet un reçu numéroté")
    void cashCollectionSettlesInstallmentAndIssuesReceipt() {
        ReceiptEntity receipt = offlineCollectionService.collect(OfflineCollectionForm.builder()
                .installmentId(installment.getId())
                .channel(PaymentChannel.CASH)
                .build());

        assertThat(receipt.getNumber()).matches("\\d{4}-\\d{6}");
        assertThat(receipt.getSequenceNumber()).isEqualTo(1L);
        assertThat(receipt.getAmount()).isEqualByComparingTo("50000");
        assertThat(receipt.getEstablishment().getId()).isEqualTo(establishment.getId());

        InstallmentEntity settled = installmentRepository.findById(installment.getId()).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(InstallmentStatus.PAID);
        assertThat(settled.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("aucune commission YPYit n'est prélevée sur un encaissement hors ligne")
    void offlineCollectionCarriesNoCommission() {
        ReceiptEntity receipt = offlineCollectionService.collect(OfflineCollectionForm.builder()
                .installmentId(installment.getId())
                .channel(PaymentChannel.CHECK)
                .reference("CHQ-4412")
                .build());

        var intent = paymentIntentRepository.findById(receipt.getPaymentIntent().getId()).orElseThrow();
        assertThat(intent.getAmountCommission()).isEqualByComparingTo("0");
        assertThat(intent.getAmountSchool()).isEqualByComparingTo("50000");
        assertThat(intent.getStatus()).isEqualTo(PaymentIntentStatus.SUCCEEDED);
        assertThat(intent.getChannel()).isEqualTo(PaymentChannel.CHECK);
        assertThat(intent.getInternalReference())
                .as("un encaissement au guichet ne passe pas par l'agrégateur")
                .isNull();
    }

    @Test
    @DisplayName("une double saisie au guichet est refusée")
    void doubleCollectionIsRejected() {
        offlineCollectionService.collect(OfflineCollectionForm.builder()
                .installmentId(installment.getId()).channel(PaymentChannel.CASH).build());

        assertThatThrownBy(() -> offlineCollectionService.collect(OfflineCollectionForm.builder()
                .installmentId(installment.getId()).channel(PaymentChannel.CASH).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already");

        assertThat(paymentIntentRepository.findAll().stream()
                .filter(i -> i.getInstallment().getId().equals(installment.getId())).toList())
                .as("un seul règlement doit être enregistré")
                .hasSize(1);
    }

    @Test
    @DisplayName("une école ne peut pas encaisser la tranche d'un élève d'une autre école")
    void cannotCollectForAnotherEstablishment() {
        EstablishmentEntity other = establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("Autre école " + UUID.randomUUID()).active(true).build());
        InstallmentEntity foreign = anInstallmentOf(other, new BigDecimal("30000"));

        assertThatThrownBy(() -> offlineCollectionService.collect(OfflineCollectionForm.builder()
                .installmentId(foreign.getId()).channel(PaymentChannel.CASH).build()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("le canal ONLINE est refusé par le guichet")
    void onlineChannelIsRejected() {
        assertThatThrownBy(() -> offlineCollectionService.collect(OfflineCollectionForm.builder()
                .installmentId(installment.getId()).channel(PaymentChannel.ONLINE).build()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("la liste des reçus se lit sans entraîner tout le graphe d'objets")
    void listsReceiptsWithoutDraggingTheWholeGraph() {
        offlineCollectionService.collect(OfflineCollectionForm.builder()
                .installmentId(installment.getId()).channel(PaymentChannel.CASH).build());

        // Avec les relations en chargement immédiat, lire un reçu tirait la tentative de paiement,
        // la tranche, l'élève, l'établissement et ses collections : Hibernate produisait une
        // requête au produit cartésien qui ne rendait jamais la main.
        var page = receiptService.search(null, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getNumber()).matches("\\d{4}-\\d{6}");
    }

    @Test
    @DisplayName("le reçu se rend en PDF exploitable")
    void rendersReceiptAsPdf() throws Exception {
        ReceiptEntity receipt = offlineCollectionService.collect(OfflineCollectionForm.builder()
                .installmentId(installment.getId()).channel(PaymentChannel.CASH).build());

        byte[] pdf = receiptPdfRenderer.render(receipt);

        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        // On lit le texte réellement rendu plutôt que de se contenter de la signature et d'une
        // taille : un gabarit cassé produit une page blanche, qui est un PDF parfaitement valide
        // et de taille plausible. Sans cette lecture, le test validerait un reçu vide.
        String texte;
        // PDFBox 2.x est la version tirée par openhtmltopdf : le chargement passe par
        // PDDocument.load, `Loader` n'apparaissant qu'en 3.x.
        try (org.apache.pdfbox.pdmodel.PDDocument document =
                     org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
            texte = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
        }

        // Les retours à la ligne du rendu tombent où la mise en page les place : on normalise
        // les espaces avant de comparer, sinon un simple recadrage du gabarit casse le test.
        String platEtNormalise = texte.replaceAll("\\s+", " ");

        assertThat(platEtNormalise)
                .contains("REÇU DE PAIEMENT")
                .contains(receipt.getNumber())
                .contains("Aaron Koffi")
                .contains("Matricule")
                .contains("Espèces")
                .contains("50 000 FCFA");

        // Aucun payeur n'a été déclaré au comptoir : le reçu ne doit pas désigner l'agent comme
        // ayant réglé. Il a saisi l'opération, il n'a pas payé.
        assertThat(platEtNormalise)
                .as("l'agent qui encaisse n'est pas le payeur")
                .doesNotContain("Awa Traoré")
                .doesNotContain("Réglé par");
        assertThat(receiptPdfRenderer.fileNameOf(receipt))
                .isEqualTo("recu-" + receipt.getNumber() + ".pdf");
    }

    private InstallmentEntity anInstallmentOf(EstablishmentEntity school, BigDecimal amount) {
        StudentEntity student = studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Aaron").lastName("Koffi")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2012, 4, 3))
                .establishment(school).build());
        FeeEntity fee = feeRepository.saveAndFlush(FeeEntity.builder()
                .name("Scolarité " + UUID.randomUUID()).price(amount)
                .establishment(school).build());
        StudentFeeEntity studentFee = studentFeeRepository.saveAndFlush(StudentFeeEntity.builder()
                .student(student).fee(fee).build());
        return installmentRepository.saveAndFlush(InstallmentEntity.builder()
                .studentFee(studentFee).label("1er versement").amount(amount)
                .dueDate(LocalDate.of(2026, 10, 15))
                .status(InstallmentStatus.PENDING).build());
    }

    private void authenticateAsSchoolUserOf(EstablishmentEntity school) {
        String email = "comptable-" + UUID.randomUUID() + "@nelima.ci";
        userRepository.saveAndFlush(EstablishmentUserEntity.builder()
                .firstName("Awa").lastName("Traoré")
                .establishment(school)
                .contacts(Set.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value(email).isPrimary(true).build()))
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
