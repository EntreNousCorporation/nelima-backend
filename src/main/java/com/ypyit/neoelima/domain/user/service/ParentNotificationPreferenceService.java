package com.ypyit.neoelima.domain.user.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.enums.NotificationChannel;
import com.ypyit.neoelima.domain.establishment.enums.NotificationEvent;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.NotificationPreferenceService;
import com.ypyit.neoelima.domain.user.dto.ParentNotificationSettingsDto;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.entity.UserNotificationPreferenceEntity;
import com.ypyit.neoelima.domain.user.form.ParentNotificationPreferenceForm;
import com.ypyit.neoelima.domain.user.form.QuietHoursForm;
import com.ypyit.neoelima.domain.user.repository.UserNotificationPreferenceRepository;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ce qu'un parent accepte de recevoir.
 *
 * <p>L'autre moitié du sujet : {@link NotificationPreferenceService} dit ce que l'<em>école</em>
 * accepte d'envoyer. Les deux se croisent par un <strong>ET</strong> au moment de l'envoi. Aucune ne
 * force l'autre — une école ne peut pas imposer un SMS à qui n'en veut pas, et un parent ne peut pas
 * s'abonner à ce que son école n'envoie pas.
 *
 * <p>Le réglage n'a de valeur que parce qu'il est consulté à l'envoi : {@code ReminderDispatcher} et
 * {@code ReceiptPushNotifier} passent par {@link #accepts}. Une matrice que personne n'interroge est
 * une promesse que le premier essai contredit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParentNotificationPreferenceService {

    /** Les heures calmes s'entendent à l'heure d'Abidjan, pas à celle du serveur. */
    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");

    private static final String RECEIPT_EMAIL_REASON =
            "Le reçu est une pièce comptable : le courriel qui le porte ne peut pas être coupé.";

    private final UserNotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final NotificationPreferenceService schoolPreferenceService;
    private final StudentRepository studentRepository;

    /* ---------- Lecture et écriture par le parent ---------- */

    public ParentNotificationSettingsDto mySettings() {
        UserEntity user = this.currentUserProvider.currentUser();

        return ParentNotificationSettingsDto.builder()
                .quietFrom(user.getQuietFrom())
                .quietTo(user.getQuietTo())
                .events(Arrays.stream(NotificationEvent.values())
                        .map(event -> ParentNotificationSettingsDto.EventSettingDto.builder()
                                .event(event)
                                .channels(event.supportedChannels().stream()
                                        .map(channel -> this.channelSetting(user, event, channel))
                                        .toList())
                                .build())
                        .toList())
                .build();
    }

    @Transactional
    public ParentNotificationSettingsDto update(ParentNotificationPreferenceForm form) {
        UserEntity user = this.currentUserProvider.currentUser();
        NotificationEvent event = form.getEvent();
        NotificationChannel channel = form.getChannel();

        if (!event.supports(channel)) {
            throw new BadRequestException(String.format(
                    "Le canal %s n'est pas raccordé à l'événement %s.", channel, event));
        }
        if (event.isLocked(channel) && !form.isEnabled()) {
            throw new BadRequestException(RECEIPT_EMAIL_REASON);
        }

        UserNotificationPreferenceEntity entity = this.preferenceRepository
                .findByUser_IdAndEventAndChannel(user.getId(), event, channel)
                .orElseGet(() -> UserNotificationPreferenceEntity.builder()
                        .user(user).event(event).channel(channel).build());
        entity.setEnabled(form.isEnabled());
        this.preferenceRepository.saveAndFlush(entity);

        log.info("PARENT_NOTIFICATION_PREFERENCE_SET: {}/{} = {} pour le compte {}",
                event, channel, form.isEnabled(), user.getId());
        return this.mySettings();
    }

    /**
     * Règle — ou lève — les heures calmes.
     *
     * <p>Les deux bornes vont ensemble : n'en poser qu'une donnerait une fenêtre sans fin. Les
     * effacer toutes deux rend le silence à son état normal, qui est de ne pas exister.
     */
    @Transactional
    public ParentNotificationSettingsDto updateQuietHours(QuietHoursForm form) {
        boolean from = Objects.nonNull(form.getQuietFrom());
        boolean to = Objects.nonNull(form.getQuietTo());
        if (from != to) {
            throw new BadRequestException(
                    "Les heures calmes se règlent par leurs deux bornes, ou pas du tout.");
        }
        if (from && Objects.equals(form.getQuietFrom(), form.getQuietTo())) {
            // Une fenêtre nulle se lirait comme « jamais » ou « toujours » selon l'implémentation.
            throw new BadRequestException("Les deux bornes des heures calmes doivent différer.");
        }

        UserEntity user = this.currentUserProvider.currentUser();
        user.setQuietFrom(form.getQuietFrom());
        user.setQuietTo(form.getQuietTo());
        this.userRepository.saveAndFlush(user);

        log.info("PARENT_QUIET_HOURS_SET: {} → {} pour le compte {}",
                form.getQuietFrom(), form.getQuietTo(), user.getId());
        return this.mySettings();
    }

    /* ---------- Consulté au moment de l'envoi ---------- */

    /**
     * Ce compte accepte-t-il cette notification, maintenant ?
     *
     * <p>Trois conditions : l'événement le prévoit, le parent ne l'a pas coupé, et l'on n'est pas
     * dans ses heures calmes. Un canal verrouillé passe quoi qu'il arrive — le courriel qui porte un
     * reçu n'est pas débrayable, et ne se tait pas la nuit non plus : c'est une pièce comptable, pas
     * une sonnerie.
     */
    public boolean accepts(UUID userId, NotificationEvent event, NotificationChannel channel) {
        if (!event.supports(channel)) {
            return false;
        }
        if (event.isLocked(channel)) {
            return true;
        }
        if (Objects.isNull(userId)) {
            // Sans destinataire identifiable, on s'en tient au défaut plutôt que de taire un envoi.
            return event.defaultForParent(channel);
        }

        // Le défaut du parent, et non celui de l'école : sa table ne fait qu'enlever. Voir
        // NotificationEvent.defaultForParent — s'y tromper éteint le SMS de tout le monde.
        boolean wanted = this.preferenceRepository
                .findByUser_IdAndEventAndChannel(userId, event, channel)
                .map(UserNotificationPreferenceEntity::isEnabled)
                .orElseGet(() -> event.defaultForParent(channel));
        if (!wanted) {
            return false;
        }
        return !this.isQuietNow(userId);
    }

    /**
     * Sommes-nous dans les heures calmes de ce compte ?
     *
     * <p>La fenêtre peut franchir minuit — 22h00 → 06h00 est le cas courant, et le plus utile. Le
     * test se fait donc en deux temps selon que la borne de fin précède ou suit celle de début.
     */
    public boolean isQuietNow(UUID userId) {
        UserEntity user = this.userRepository.findById(userId).orElse(null);
        if (Objects.isNull(user)
                || Objects.isNull(user.getQuietFrom())
                || Objects.isNull(user.getQuietTo())) {
            return false;
        }

        LocalTime now = LocalTime.now(ABIDJAN);
        LocalTime from = user.getQuietFrom();
        LocalTime to = user.getQuietTo();

        if (from.isBefore(to)) {
            return !now.isBefore(from) && now.isBefore(to);
        }
        // Fenêtre à cheval sur minuit : on est dedans si l'on est après le début OU avant la fin.
        return !now.isBefore(from) || now.isBefore(to);
    }

    /**
     * Ne garde, d'une liste de destinataires, que ceux qui acceptent.
     *
     * <p>Rendu pour les envois groupés, qui connaissent leurs comptes mais pas leurs réglages.
     */
    public List<UUID> accepting(List<UUID> userIds, NotificationEvent event,
                                NotificationChannel channel) {
        return userIds.stream().filter(id -> this.accepts(id, event, channel)).toList();
    }

    private ParentNotificationSettingsDto.ChannelSettingDto channelSetting(
            UserEntity user, NotificationEvent event, NotificationChannel channel) {
        boolean locked = event.isLocked(channel);
        // L'école d'un parent : la première de ses enfants suffit à savoir si le canal est ouvert.
        // Un parent dont les enfants sont dans deux écoles voit le réglage de la plus permissive —
        // afficher un interrupteur barré pour une seule des deux serait plus déroutant qu'utile.
        boolean allowedBySchool = locked || this.anySchoolAllows(user, event, channel);

        return ParentNotificationSettingsDto.ChannelSettingDto.builder()
                .channel(channel)
                .enabled(this.preferenceRepository
                        .findByUser_IdAndEventAndChannel(user.getId(), event, channel)
                        .map(UserNotificationPreferenceEntity::isEnabled)
                        .orElseGet(() -> event.defaultForParent(channel)))
                .allowedBySchool(allowedBySchool)
                .locked(locked)
                .lockedReason(locked ? RECEIPT_EMAIL_REASON : null)
                .build();
    }

    /**
     * Au moins une des écoles de ses enfants laisse-t-elle passer ce canal ?
     *
     * <p>Un parent dont les enfants sont dans deux écoles voit le réglage de la plus permissive :
     * barrer l'interrupteur pour une seule des deux serait plus déroutant qu'utile, et le croisement
     * réel se fait de toute façon école par école à l'envoi.
     *
     * <p>Sans enfant rattaché, on ne barre rien : le compte est neuf, et lui montrer des
     * interrupteurs inertes serait le pire premier écran de réglages.
     */
    private boolean anySchoolAllows(UserEntity user, NotificationEvent event,
                                    NotificationChannel channel) {
        List<UUID> schools = this.studentRepository.findByParentUsers_Id(user.getId()).stream()
                .map(StudentEntity::getEstablishment)
                .filter(Objects::nonNull)
                .map(EstablishmentEntity::getId)
                .distinct()
                .toList();
        if (schools.isEmpty()) {
            return true;
        }
        return schools.stream()
                .anyMatch(schoolId -> this.schoolPreferenceService.isEnabled(schoolId, event, channel));
    }
}
