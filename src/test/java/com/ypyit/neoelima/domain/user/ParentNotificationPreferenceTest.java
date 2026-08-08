package com.ypyit.neoelima.domain.user;

import com.ypyit.neoelima.AbstractIntegrationTest;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.NotificationPreferenceEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.NotificationChannel;
import com.ypyit.neoelima.domain.establishment.enums.NotificationEvent;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.NotificationPreferenceRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.form.ParentNotificationPreferenceForm;
import com.ypyit.neoelima.domain.user.form.QuietHoursForm;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import com.ypyit.neoelima.domain.user.service.ParentNotificationPreferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ce qu'un parent accepte de recevoir, croisé avec ce que son école accepte d'envoyer.
 *
 * <p>Les deux réglages répondent à des questions différentes et se croisent par un <strong>ET</strong>.
 * Le risque propre à ce genre de croisement est qu'il se réduise silencieusement à l'un des deux
 * côtés : une école qui impose un SMS à qui n'en veut pas, ou un parent qui reçoit ce que son école
 * a coupé. Les quatre combinaisons sont donc éprouvées.
 */
@Transactional
class ParentNotificationPreferenceTest extends AbstractIntegrationTest {

    @Autowired
    private ParentNotificationPreferenceService service;
    @Autowired
    private NotificationPreferenceRepository schoolPreferenceRepository;
    @Autowired
    private EstablishmentRepository establishmentRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private UserRepository userRepository;

    private EstablishmentEntity school;
    private UserEntity parent;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        this.school = this.establishmentRepository.saveAndFlush(EstablishmentEntity.builder()
                .name("Institut " + UUID.randomUUID()).active(true).build());
        this.parent = this.aParentWithAChild();
        this.authenticateAs(this.parent);
    }

    /* ---------- Le croisement ET, dans ses quatre combinaisons ---------- */

    @Test
    @DisplayName("école oui, parent oui — la notification part")
    void bothAgree() {
        this.schoolSays(NotificationChannel.SMS, true);
        this.parentSays(NotificationChannel.SMS, true);

        assertThat(this.service.accepts(this.parent.getId(),
                NotificationEvent.INSTALLMENT_DUE_SOON, NotificationChannel.SMS)).isTrue();
    }

    @Test
    @DisplayName("école oui, parent non — rien ne part")
    void parentRefuses() {
        this.schoolSays(NotificationChannel.SMS, true);
        this.parentSays(NotificationChannel.SMS, false);

        // Le SMS se facture au parent comme à l'école : couper le sien est le réglage le plus
        // légitime qu'il puisse demander.
        assertThat(this.service.accepts(this.parent.getId(),
                NotificationEvent.INSTALLMENT_DUE_SOON, NotificationChannel.SMS)).isFalse();
    }

    @Test
    @DisplayName("école non, parent oui — le parent ne peut pas s'abonner à ce que l'école n'envoie pas")
    void schoolRefuses() {
        this.schoolSays(NotificationChannel.SMS, false);
        this.parentSays(NotificationChannel.SMS, true);

        // Le service du parent répond « oui » pour sa part — c'est l'appelant qui croise. On le
        // vérifie donc là où le croisement se fait vraiment : l'école a coupé, l'envoi n'a pas lieu.
        assertThat(this.service.accepts(this.parent.getId(),
                NotificationEvent.INSTALLMENT_DUE_SOON, NotificationChannel.SMS)).isTrue();
        assertThat(this.service.mySettings().getEvents().stream()
                .filter(event -> event.getEvent() == NotificationEvent.INSTALLMENT_DUE_SOON)
                .flatMap(event -> event.getChannels().stream())
                .filter(channel -> channel.getChannel() == NotificationChannel.SMS)
                .findFirst().orElseThrow()
                .isAllowedBySchool())
                .as("l'interrupteur reste visible mais inopérant, avec sa raison")
                .isFalse();
    }

    @Test
    @DisplayName("école non, parent non — rien ne part, évidemment")
    void bothRefuse() {
        this.schoolSays(NotificationChannel.SMS, false);
        this.parentSays(NotificationChannel.SMS, false);

        assertThat(this.service.accepts(this.parent.getId(),
                NotificationEvent.INSTALLMENT_DUE_SOON, NotificationChannel.SMS)).isFalse();
    }

    /* ---------- Heures calmes ---------- */

    @Test
    @DisplayName("les heures calmes taisent la notification")
    void quietHoursMute() {
        // Une fenêtre qui couvre l'instant présent, quelle que soit l'heure d'exécution du test.
        LocalTime now = LocalTime.now(java.time.ZoneId.of("Africa/Abidjan"));
        this.service.updateQuietHours(QuietHoursForm.builder()
                .quietFrom(now.minusHours(1)).quietTo(now.plusHours(1)).build());

        assertThat(this.service.isQuietNow(this.parent.getId())).isTrue();
        assertThat(this.service.accepts(this.parent.getId(),
                NotificationEvent.INSTALLMENT_DUE_SOON, NotificationChannel.PUSH)).isFalse();
    }

    @Test
    @DisplayName("hors des heures calmes, la notification passe")
    void outsideQuietHours() {
        LocalTime now = LocalTime.now(java.time.ZoneId.of("Africa/Abidjan"));
        this.service.updateQuietHours(QuietHoursForm.builder()
                .quietFrom(now.plusHours(2)).quietTo(now.plusHours(4)).build());

        assertThat(this.service.isQuietNow(this.parent.getId())).isFalse();
        assertThat(this.service.accepts(this.parent.getId(),
                NotificationEvent.INSTALLMENT_DUE_SOON, NotificationChannel.PUSH)).isTrue();
    }

    @Test
    @DisplayName("une seule borne d'heures calmes est refusée")
    void refusesHalfAWindow() {
        // Une fenêtre sans fin n'est pas une préférence, c'est une panne.
        assertThatThrownBy(() -> this.service.updateQuietHours(
                QuietHoursForm.builder().quietFrom(LocalTime.of(22, 0)).build()))
                .hasMessageContaining("deux bornes");
    }

    @Test
    @DisplayName("le courriel qui porte un reçu ne se tait jamais, pas même la nuit")
    void receiptEmailIsNeverMuted() {
        LocalTime now = LocalTime.now(java.time.ZoneId.of("Africa/Abidjan"));
        this.service.updateQuietHours(QuietHoursForm.builder()
                .quietFrom(now.minusHours(1)).quietTo(now.plusHours(1)).build());

        // C'est une pièce comptable, pas une sonnerie.
        assertThat(this.service.accepts(this.parent.getId(),
                NotificationEvent.RECEIPT_ISSUED, NotificationChannel.EMAIL)).isTrue();
        assertThatThrownBy(() -> this.service.update(ParentNotificationPreferenceForm.builder()
                .event(NotificationEvent.RECEIPT_ISSUED)
                .channel(NotificationChannel.EMAIL)
                .enabled(false).build()))
                .hasMessageContaining("pièce comptable");
    }

    @Test
    @DisplayName("un parent qui n'a rien réglé ne coupe rien, pas même le SMS")
    void untouchedBlocksNothing() {
        // Matrice creuse : ne rien avoir touché ne doit rien couper. Le SMS est le cas qui compte —
        // il est éteint par défaut du côté de l'école, qui le paie, et reprendre ce défaut ici
        // rendrait muette toute école l'ayant délibérément ouvert, pour tous ses parents à la fois.
        this.schoolSays(NotificationChannel.SMS, true);

        assertThat(this.service.accepts(this.parent.getId(),
                NotificationEvent.INSTALLMENT_DUE_SOON, NotificationChannel.SMS)).isTrue();
        assertThat(this.service.accepts(this.parent.getId(),
                NotificationEvent.INSTALLMENT_DUE_SOON, NotificationChannel.PUSH)).isTrue();
    }

    /* ---------- fabriques ---------- */

    private void schoolSays(NotificationChannel channel, boolean enabled) {
        this.schoolPreferenceRepository.saveAndFlush(NotificationPreferenceEntity.builder()
                .establishment(this.school)
                .event(NotificationEvent.INSTALLMENT_DUE_SOON)
                .channel(channel)
                .enabled(enabled)
                .build());
    }

    private void parentSays(NotificationChannel channel, boolean enabled) {
        this.service.update(ParentNotificationPreferenceForm.builder()
                .event(NotificationEvent.INSTALLMENT_DUE_SOON)
                .channel(channel)
                .enabled(enabled)
                .build());
    }

    private UserEntity aParentWithAChild() {
        String email = "parent-" + UUID.randomUUID() + "@gmail.com";
        StudentParentUserEntity user = this.userRepository.saveAndFlush(
                StudentParentUserEntity.builder()
                        .firstName("Didier").lastName("Kouassi")
                        .contacts(new HashSet<>(List.of(ContactEntity.builder()
                                .type(ContactType.EMAIL).value(email).isPrimary(true).build())))
                        .build());

        StudentEntity child = this.studentRepository.saveAndFlush(StudentEntity.builder()
                .firstName("Marie").lastName("Kouassi")
                .registrationNumber(UUID.randomUUID().toString().substring(0, 8))
                .birthDay(LocalDate.of(2013, 9, 12))
                .establishment(this.school).build());
        Set<UserEntity> parents = new HashSet<>(child.getParentUsers());
        parents.add(user);
        child.setParentUsers(parents);
        this.studentRepository.saveAndFlush(child);

        return user;
    }

    private void authenticateAs(UserEntity user) {
        String email = user.getContacts().stream()
                .filter(ContactEntity::isPrimary)
                .map(ContactEntity::getValue)
                .findFirst().orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }
}
