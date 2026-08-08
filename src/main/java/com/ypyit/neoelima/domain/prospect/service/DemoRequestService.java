package com.ypyit.neoelima.domain.prospect.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.common.service.email.enums.EmailTemplateType;
import com.ypyit.neoelima.common.service.email.service.EmailService;
import com.ypyit.neoelima.config.properties.EmailConfigProperties;
import com.ypyit.neoelima.domain.prospect.dto.DemoRequestDto;
import com.ypyit.neoelima.domain.prospect.entity.DemoRequestEntity;
import com.ypyit.neoelima.domain.prospect.entity.DemoRequestStatus;
import com.ypyit.neoelima.domain.prospect.form.DemoRequestForm;
import com.ypyit.neoelima.domain.prospect.repository.DemoRequestRepository;
import com.ypyit.neoelima.domain.subscription.service.SubscriptionPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Demandes de démonstration venues du site public.
 *
 * <p>La route de dépôt est ouverte : c'est la seule de l'application que personne n'authentifie.
 * Deux conséquences en découlent, et elles sont traitées ici plutôt que dans le contrôleur, parce
 * qu'elles tiennent au métier et non au transport.
 *
 * <p><strong>Rien ne se répond au visiteur.</strong> Le service ne dit jamais si une adresse est
 * déjà connue, ni combien de demandes existent : une route publique qui distingue ses cas de refus
 * devient un moyen de sonder la base.
 *
 * <p><strong>Le débit est borné.</strong> Un pot de miel écarte les automates de formulaire, et
 * deux compteurs — par adresse et pour l'ensemble — évitent qu'un envoi en boucle ne remplisse la
 * table. Aucun captcha : un directeur d'école sur un téléphone ne doit pas avoir d'énigme à
 * résoudre pour demander une démonstration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoRequestService {

    /** Fenêtre des compteurs anti-abus. */
    private static final Duration WINDOW = Duration.ofHours(1);

    /** Une même école n'a pas trois choses à demander dans l'heure. */
    private static final int MAX_PER_EMAIL = 3;

    /** Plafond global : au-delà, c'est un automate, pas une rentrée scolaire. */
    private static final int MAX_PER_WINDOW = 60;

    private final DemoRequestRepository repository;
    private final SubscriptionPlanService planService;
    private final EmailService emailService;
    private final EmailConfigProperties emailConfigProperties;

    /* ---------------- Dépôt public ---------------- */

    @Transactional
    public void submit(DemoRequestForm form) {
        // Pot de miel : le champ est invisible dans la page. Rempli, c'est un automate. On répond
        // comme si tout allait bien — dire « refusé » lui apprendrait à le contourner.
        if (Objects.nonNull(form.getWebsite()) && !form.getWebsite().isBlank()) {
            log.info("DEMO_REQUEST_TRAPPED: pot de miel rempli");
            return;
        }

        Instant since = Instant.now().minus(WINDOW);
        String email = form.getEmail().trim();
        if (this.repository.countByEmailIgnoreCaseAndCreatedAtAfter(email, since) >= MAX_PER_EMAIL
                || this.repository.countByCreatedAtAfter(since) >= MAX_PER_WINDOW) {
            log.warn("DEMO_REQUEST_THROTTLED: {} demandes récentes", MAX_PER_EMAIL);
            // Silencieux, pour la même raison : un refus distinct renseignerait sur la base.
            return;
        }

        DemoRequestEntity request = this.repository.saveAndFlush(DemoRequestEntity.builder()
                .schoolName(form.getSchoolName().trim())
                .contactName(form.getContactName().trim())
                .email(email)
                .phone(this.trimmed(form.getPhone()))
                .city(this.trimmed(form.getCity()))
                .studentCount(form.getStudentCount())
                .message(this.trimmed(form.getMessage()))
                .sourcePage(this.trimmed(form.getSourcePage()))
                .status(DemoRequestStatus.PENDING)
                .build());

        log.info("DEMO_REQUEST_RECEIVED: {} ({})", request.getSchoolName(), request.getId());
        this.notifyPlatform(request);
    }

    /**
     * Alerte adressée à YPYit.
     *
     * <p>Après enregistrement et hors du chemin critique : un serveur de courriel indisponible ne
     * doit pas faire perdre une demande au visiteur, qui n'y est pour rien et ne réessaiera pas.
     */
    private void notifyPlatform(DemoRequestEntity request) {
        try {
            Context context = new Context();
            context.setVariable("email", this.emailConfigProperties.getSupportEmail());
            context.setVariable("schoolName", request.getSchoolName());
            context.setVariable("contactName", request.getContactName());
            context.setVariable("contactEmail", request.getEmail());
            context.setVariable("phone", request.getPhone());
            context.setVariable("city", request.getCity());
            context.setVariable("studentCount", request.getStudentCount());
            context.setVariable("message", request.getMessage());
            this.emailService.send(context, EmailTemplateType.DEMO_REQUEST);
        } catch (Exception e) {
            log.error("DEMO_REQUEST_MAIL_FAILED: {}", request.getId(), e);
        }
    }

    /* ---------------- Console ---------------- */

    @Transactional(readOnly = true)
    public List<DemoRequestDto> all() {
        return this.repository.findByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return this.repository.countByStatus(DemoRequestStatus.PENDING);
    }

    @Transactional
    public DemoRequestDto setStatus(UUID id, DemoRequestStatus status, String note) {
        if (Objects.isNull(status)) {
            throw new BadRequestException("Le statut est obligatoire.");
        }
        DemoRequestEntity request = this.repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Demande de démonstration introuvable."));

        request.setStatus(status);
        // La date de traitement suit le statut : revenir en attente efface la trace, sinon la fiche
        // dirait « traitée le 3 » d'une demande qu'on vient de rouvrir.
        request.setHandledAt(status == DemoRequestStatus.PENDING ? null : Instant.now());
        request.setHandledNote(this.trimmed(note));
        return this.toDto(this.repository.saveAndFlush(request));
    }

    private DemoRequestDto toDto(DemoRequestEntity request) {
        return DemoRequestDto.builder()
                .id(request.getId())
                .schoolName(request.getSchoolName())
                .contactName(request.getContactName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .city(request.getCity())
                .studentCount(request.getStudentCount())
                .message(request.getMessage())
                .status(request.getStatus())
                .createdAt(request.getCreatedAt())
                .handledAt(request.getHandledAt())
                .handledNote(request.getHandledNote())
                .sourcePage(request.getSourcePage())
                .suggestedPlan(Objects.isNull(request.getStudentCount()) ? null
                        : this.planService.suggestedFor(request.getStudentCount()))
                .build();
    }

    private String trimmed(String value) {
        return Objects.isNull(value) || value.isBlank() ? null : value.trim();
    }
}
