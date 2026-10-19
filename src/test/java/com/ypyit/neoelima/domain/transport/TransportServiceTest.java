package com.ypyit.neoelima.domain.transport;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.service.push.PushNotificationService;
import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.InstallmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEnrollmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.repository.AcademicYearRepository;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.InstallmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentEnrollmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.transport.dto.TransportDtos;
import com.ypyit.neoelima.domain.transport.enums.LineStatus;
import com.ypyit.neoelima.domain.transport.form.TransportForms;
import com.ypyit.neoelima.domain.transport.service.TransportService;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Transport : lignes et arrêts, abonnement facturé au mois dans la limite des places, suivi du jour
 * qui prévient les familles.
 */
@Transactional
class TransportServiceTest extends AbstractIntegrationTest {

    @MockitoBean
    private PushNotificationService pushNotificationService;
    @Autowired
    private TransportService transportService;
    @Autowired
    private AcademicYearRepository academicYearRepository;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private StudentEnrollmentRepository enrollmentRepository;
    @Autowired
    private InstallmentRepository installmentRepository;
    @Autowired
    private UserRepository userRepository;

    private EstablishmentEntity school;
    private AcademicYearEntity year;
    private StudentParentUserEntity parent;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.year = this.academicYearRepository.saveAndFlush(AcademicYearEntity.builder()
                .label("2026-2027").startDate(LocalDate.of(2026, 9, 1)).endDate(LocalDate.of(2027, 6, 30))
                .active(true).build());
        this.school = this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("École " + UUID.randomUUID()).active(true).isPrimary(true).build());
        this.parent = this.userRepository.saveAndFlush(StudentParentUserEntity.builder()
                .firstName("Jean").lastName("Kouassi").contacts(this.email()).build());
        String director = "direction-" + UUID.randomUUID() + "@ecole.ci";
        this.userRepository.saveAndFlush(EstablishmentUserEntity.builder().firstName("Awa").lastName("Traoré")
                .establishment(this.school).contacts(this.email(director)).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(director, "n/a", List.of()));
    }

    @Test
    @DisplayName("une ligne garde l'ordre de ses arrêts, et un arrêt renommé garde ses abonnés")
    void linesKeepTheirStops() {
        TransportDtos.Line line = this.line("Ligne 1", 2, "Riviera 3", "Palmeraie");
        StudentEntity awa = this.student("KONE");
        this.subscribe(awa, line, line.getStops().getFirst().getId());

        TransportForms.Line form = this.form("Ligne 1", 2, "Riviera 3 — carrefour", "Palmeraie");
        form.getStops().getFirst().setId(UUID.fromString(line.getStops().getFirst().getId()));
        TransportDtos.Line updated = this.transportService.saveLine(UUID.fromString(line.getId()), form);

        assertThat(updated.getItinerary()).isEqualTo("Riviera 3 — carrefour → Palmeraie → École");
        assertThat(updated.getStops().getFirst().getRiders()).isEqualTo(1);
    }

    @Test
    @DisplayName("l'abonnement se facture au mois, et un car complet refuse de nouveaux élèves")
    void billsMonthlyWithinCapacity() {
        TransportDtos.Line line = this.line("Ligne 2", 1, "Angré");
        StudentEntity awa = this.student("KONE");
        this.subscribe(awa, line, null);

        List<InstallmentEntity> months = this.installmentRepository.findAll().stream()
                .filter(i -> i.getStudentFee().getStudent().getId().equals(awa.getId())).toList();
        assertThat(months).hasSize(9);
        assertThat(months).allSatisfy(m -> assertThat(m.getAmount()).isEqualByComparingTo("12000"));

        assertThatThrownBy(() -> this.subscribe(this.student("KOUAME"), line, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("un retard signalé prévient les familles de la ligne et apparaît en alerte")
    void delaysWarnFamilies() {
        TransportDtos.Line line = this.line("Ligne 3", 10, "Zone 4", "Biétry");
        StudentEntity awa = this.student("KONE");
        awa.getParentUsers().add(this.parent);
        this.studentRepository.saveAndFlush(awa);
        this.subscribe(awa, line, null);

        TransportForms.Status arrived = new TransportForms.Status();
        arrived.setStatus(LineStatus.ARRIVED);
        this.transportService.setStatus(UUID.fromString(line.getId()), arrived);
        verify(this.pushNotificationService, never()).send(any(), any(), any(), anyMap());

        TransportForms.Status delayed = new TransportForms.Status();
        delayed.setStatus(LineStatus.DELAYED);
        delayed.setDelayMinutes(12);
        this.transportService.setStatus(UUID.fromString(line.getId()), delayed);

        verify(this.pushNotificationService).send(argThat(ids -> ids.contains(this.parent.getId())),
                eq("Transport scolaire"), argThat(m -> m.contains("12 min")), anyMap());
        assertThat(this.transportService.overview().getAlerts()).extracting(TransportDtos.Line::getName)
                .containsExactly("Ligne 3");
    }

    @Test
    @DisplayName("un dossier chauffeur est complet avec téléphone, permis valide et les quatre pièces")
    void driverDossier() {
        TransportForms.Driver form = new TransportForms.Driver();
        form.setFirstName("Firmin");
        form.setLastName("Konan");
        form.setPhone("07 48 22 10 05");
        form.setLicenseCategory("D");
        form.setLicenseExpiry(LocalDate.now().plusYears(2));
        form.setDocuments(List.of("LICENSE", "ID", "RECORD"));
        TransportDtos.Driver incomplete = this.transportService.saveDriver(null, form);
        assertThat(incomplete.isDossierComplete()).isFalse();
        assertThat(incomplete.getLastName()).isEqualTo("KONAN");

        form.setDocuments(List.of("LICENSE", "ID", "RECORD", "MEDICAL"));
        assertThat(this.transportService.saveDriver(UUID.fromString(incomplete.getId()), form).isDossierComplete()).isTrue();

        form.setLicenseExpiry(LocalDate.now().minusDays(1));
        assertThat(this.transportService.saveDriver(UUID.fromString(incomplete.getId()), form).isDossierComplete())
                .as("un permis expiré rend le dossier incomplet").isFalse();
    }

    @Test
    @DisplayName("une ligne annoncée prévient les familles de l'école, une ligne discrète non")
    void announcedLineWarnsFamilies() {
        StudentEntity awa = this.student("KONE");
        awa.getParentUsers().add(this.parent);
        this.studentRepository.saveAndFlush(awa);

        this.line("Ligne 4", 30, "Cocody");
        verify(this.pushNotificationService, never()).send(any(), any(), any(), anyMap());

        TransportForms.Line form = this.form("Ligne 5", 30, "Angré", "Djibi");
        form.setAnnounce(true);
        this.transportService.saveLine(null, form);
        verify(this.pushNotificationService).send(argThat(ids -> ids.contains(this.parent.getId())),
                eq("Transport scolaire"), argThat(m -> m.contains("Ligne 5") && m.contains("Angré")), anyMap());
    }

    private TransportDtos.Line line(String name, int capacity, String... stops) {
        return this.transportService.saveLine(null, this.form(name, capacity, stops));
    }

    private TransportForms.Line form(String name, int capacity, String... stops) {
        TransportForms.Line form = new TransportForms.Line();
        form.setName(name);
        form.setCapacity(capacity);
        form.setMonthlyPrice(new BigDecimal("12000"));
        form.setMorningDeparture(LocalTime.of(6, 30));
        for (String stop : stops) {
            TransportForms.Stop s = new TransportForms.Stop();
            s.setName(stop);
            form.getStops().add(s);
        }
        return form;
    }

    private void subscribe(StudentEntity student, TransportDtos.Line line, String stopId) {
        TransportForms.Subscription form = new TransportForms.Subscription();
        form.setStudentId(student.getId());
        form.setLineId(UUID.fromString(line.getId()));
        form.setStopId(stopId == null ? null : UUID.fromString(stopId));
        form.setStartDate(LocalDate.of(2026, 10, 1));
        this.transportService.subscribe(form);
    }

    private StudentEntity student(String lastName) {
        StudentEntity student = this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Awa").lastName(lastName).registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .establishment(this.school).build());
        this.enrollmentRepository.saveAndFlush(StudentEnrollmentEntity.builder().student(student).academicYear(this.year).build());
        return student;
    }

    private Set<ContactEntity> email() {
        return this.email("p-" + UUID.randomUUID() + "@gmail.com");
    }

    private Set<ContactEntity> email(String value) {
        return new HashSet<>(Set.of(ContactEntity.builder().type(ContactType.EMAIL).value(value).isPrimary(true).build()));
    }
}
