package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.AuditEventDto;
import com.ypyit.neoelima.domain.establishment.entity.AuditEventEntity;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.enums.AuditAction;
import com.ypyit.neoelima.domain.establishment.repository.AuditEventRepository;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Journal chronologique des actes sensibles.
 *
 * <p>Alimenté par des <strong>appels explicites</strong> aux endroits qui comptent, et non par un
 * aspect qui intercepterait tout : un journal qui consigne chaque lecture noie l'encaissement qu'on
 * y cherche.
 *
 * <p>Il complète l'audit JPA déjà en place ({@code createdBy}, {@code modifiedBy}), qui dit qui a
 * touché un objet mais oblige à ouvrir chaque objet pour le lire.
 *
 * <p>⚠️ Aucune purge n'est programmée : le journal grossit sans limite. C'est tenable au démarrage,
 * pas indéfiniment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Africa/Abidjan");

    private final AuditEventRepository auditEventRepository;
    private final EstablishmentRepository establishmentRepository;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Consigne un acte.
     *
     * <p>Un échec du journal n'annule jamais l'acte : l'argent est encaissé, l'accès est ouvert, et
     * défaire cela pour une ligne manquante serait un remède pire que le mal. L'échec part dans les
     * traces, au niveau erreur, pour qu'il ne passe pas inaperçu.
     */
    @Transactional
    public void record(UUID establishmentId, AuditAction action, String target, String details) {
        if (Objects.isNull(establishmentId)) {
            log.warn("AUDIT_SKIPPED: action {} sans établissement identifiable", action);
            return;
        }
        try {
            UserEntity actor = this.currentActor();
            EstablishmentEntity establishment = this.establishmentRepository
                    .getReferenceById(establishmentId);

            this.auditEventRepository.save(AuditEventEntity.builder()
                    .establishment(establishment)
                    .action(action)
                    .occurredAt(Instant.now())
                    .actor(actor)
                    // Recopié, non déduit à la lecture : un compte fermé ne doit pas effacer qui a
                    // agi, et c'est quand quelqu'un s'en va qu'on relit le journal.
                    .actorName(Objects.isNull(actor) ? "Système" : nameOf(actor))
                    .target(truncate(target, 255))
                    .details(truncate(details, 500))
                    .build());
        } catch (RuntimeException e) {
            log.error("AUDIT_FAILED: action {} sur {} non consignée : {}", action, target,
                    e.getMessage());
        }
    }

    /** Le journal de l'école sur une période, éventuellement restreint à un auteur. */
    @Transactional(readOnly = true)
    public List<AuditEventDto> findAll(LocalDate from, LocalDate to, UUID actorId) {
        UUID scope = this.scope();
        if (Objects.isNull(from) || Objects.isNull(to) || to.isBefore(from)) {
            throw new BadRequestException("La période demandée est invalide.");
        }

        Instant start = from.atStartOfDay(SCHOOL_ZONE).toInstant();
        // Borne haute inclusive : une journée d'école se termine à minuit à Abidjan, et demander
        // « du 1er au 5 » sans inclure le 5 ferait manquer les encaissements du jour même.
        Instant end = to.plusDays(1).atStartOfDay(SCHOOL_ZONE).toInstant();

        List<AuditEventEntity> events = Objects.isNull(actorId)
                ? this.auditEventRepository
                        .findByEstablishment_IdAndOccurredAtBetweenOrderByOccurredAtDesc(scope, start, end)
                : this.auditEventRepository
                        .findByEstablishment_IdAndActor_IdAndOccurredAtBetweenOrderByOccurredAtDesc(
                                scope, actorId, start, end);

        return events.stream().map(AuditService::toDto).toList();
    }

    private static AuditEventDto toDto(AuditEventEntity entity) {
        return AuditEventDto.builder()
                .id(entity.getId().toString())
                .action(entity.getAction())
                .occurredAt(entity.getOccurredAt())
                .actorId(Objects.isNull(entity.getActor()) ? null : entity.getActor().getId().toString())
                .actorName(entity.getActorName())
                .target(entity.getTarget())
                .details(entity.getDetails())
                .build();
    }

    /** L'auteur, ou {@code null} quand l'acte vient d'un travail programmé. */
    private UserEntity currentActor() {
        try {
            return this.currentUserProvider.currentUser();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String nameOf(UserEntity user) {
        return String.join(" ",
                Objects.toString(user.getFirstName(), ""),
                Objects.toString(user.getLastName(), "")).trim();
    }

    private static String truncate(String value, int max) {
        if (Objects.isNull(value)) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private UUID scope() {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        return scope;
    }
}
