package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.NotificationPreferenceDto;
import com.ypyit.neoelima.domain.establishment.entity.NotificationPreferenceEntity;
import com.ypyit.neoelima.domain.establishment.enums.NotificationChannel;
import com.ypyit.neoelima.domain.establishment.enums.NotificationEvent;
import com.ypyit.neoelima.domain.establishment.form.NotificationPreferenceForm;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ce que l'école accepte d'envoyer à ses familles.
 *
 * <p>Le réglage n'a de valeur que parce qu'il est consulté au moment de l'envoi :
 * {@link ReminderDispatcher} pour les rappels et les campagnes,
 * {@code ReceiptPushNotifier} pour l'annonce d'un encaissement. Une matrice que personne
 * n'interroge est une promesse que le premier essai contredit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationPreferenceService {

    /** Raison affichée à côté d'une case verrouillée. */
    private static final String RECEIPT_EMAIL_REASON =
            "Le reçu est une pièce comptable : le courriel qui le porte ne peut pas être coupé.";

    private final NotificationPreferenceRepository preferenceRepository;
    private final EstablishmentRepository establishmentRepository;
    private final CurrentUserProvider currentUserProvider;

    /** La matrice de l'école, canaux non raccordés exclus. */
    public List<NotificationPreferenceDto> findAll() {
        UUID scope = this.scope();
        return Arrays.stream(NotificationEvent.values())
                .map(event -> NotificationPreferenceDto.builder()
                        .event(event)
                        .channels(event.supportedChannels().stream()
                                .map(channel -> NotificationPreferenceDto.ChannelSettingDto.builder()
                                        .channel(channel)
                                        .enabled(this.isEnabled(scope, event, channel))
                                        .locked(event.isLocked(channel))
                                        .lockedReason(event.isLocked(channel) ? RECEIPT_EMAIL_REASON : null)
                                        .build())
                                .toList())
                        .build())
                .toList();
    }

    @Transactional
    public List<NotificationPreferenceDto> update(NotificationPreferenceForm form) {
        UUID scope = this.scope();
        NotificationEvent event = form.getEvent();
        NotificationChannel channel = form.getChannel();

        if (!event.supports(channel)) {
            // Enregistrer un réglage sans envoi derrière ferait croire l'école couverte sur un
            // canal qui n'existe pas pour cet événement.
            throw new BadRequestException(String.format(
                    "Le canal %s n'est pas raccordé à l'événement %s.", channel, event));
        }
        if (event.isLocked(channel) && !form.isEnabled()) {
            throw new BadRequestException(RECEIPT_EMAIL_REASON);
        }

        NotificationPreferenceEntity entity = this.preferenceRepository
                .findByEstablishment_IdAndEventAndChannel(scope, event, channel)
                .orElseGet(() -> NotificationPreferenceEntity.builder()
                        .establishment(this.establishmentRepository.findById(scope)
                                .orElseThrow(() -> new NotFoundException("Establishment not found")))
                        .event(event)
                        .channel(channel)
                        .build());
        entity.setEnabled(form.isEnabled());
        this.preferenceRepository.saveAndFlush(entity);

        log.info("NOTIFICATION_PREFERENCE_SET: {}/{} = {} pour l'établissement {}",
                event, channel, form.isEnabled(), scope);
        return this.findAll();
    }

    /**
     * Le canal est-il ouvert pour cet événement, dans cette école ?
     *
     * <p>Consulté au moment de l'envoi. Un canal verrouillé répond {@code true} quoi qu'en dise la
     * base : le reçu part même si une ligne contraire a été écrite par un chemin détourné.
     */
    public boolean isEnabled(UUID establishmentId, NotificationEvent event, NotificationChannel channel) {
        if (!event.supports(channel)) {
            return false;
        }
        if (event.isLocked(channel)) {
            return true;
        }
        if (Objects.isNull(establishmentId)) {
            // Sans établissement identifiable, on s'en tient au défaut plutôt que de taire un envoi.
            return event.defaultFor(channel);
        }
        return this.preferenceRepository
                .findByEstablishment_IdAndEventAndChannel(establishmentId, event, channel)
                .map(NotificationPreferenceEntity::isEnabled)
                .orElseGet(() -> event.defaultFor(channel));
    }

    private UUID scope() {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        return scope;
    }
}
