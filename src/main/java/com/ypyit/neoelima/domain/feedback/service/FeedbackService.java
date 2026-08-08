package com.ypyit.neoelima.domain.feedback.service;

import com.ypyit.neoelima.common.service.email.service.EmailService;
import com.ypyit.neoelima.common.service.email.enums.EmailTemplateType;
import com.ypyit.neoelima.config.properties.EmailConfigProperties;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.feedback.entity.FeedbackEntity;
import com.ypyit.neoelima.domain.feedback.entity.FeedbackStatus;
import com.ypyit.neoelima.domain.feedback.form.FeedbackForm;
import com.ypyit.neoelima.domain.feedback.repository.FeedbackRepository;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Les suggestions déposées depuis l'application.
 *
 * <p>Enregistrées <em>et</em> transmises à YPYit. Sur le patron de {@code DemoRequestService}, dont
 * l'expérience a montré ce qui compte : enregistrer d'abord, prévenir ensuite. Un serveur de
 * courriel indisponible ne doit pas faire perdre le retour d'un parent, qui n'y est pour rien et ne
 * réessaiera pas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    /** Fenêtre de la limitation de débit. */
    private static final Duration WINDOW = Duration.ofHours(1);

    /** Au-delà, c'est un automate ou un agacement — dans les deux cas, rien à enregistrer de plus. */
    private static final long MAX_PER_WINDOW = 5;

    private final FeedbackRepository repository;
    private final CurrentUserProvider currentUserProvider;
    private final EmailService emailService;
    private final EmailConfigProperties emailConfigProperties;

    @Transactional
    public void submit(FeedbackForm form) {
        UserEntity author = this.currentUserProvider.currentUser();

        Instant since = Instant.now().minus(WINDOW);
        if (this.repository.countByUser_IdAndCreatedAtAfter(author.getId(), since) >= MAX_PER_WINDOW) {
            // Silencieux : répondre « refusé » apprendrait surtout à contourner la limite, et le
            // parent de bonne foi qui écrit deux fois de suite n'a pas à être réprimandé.
            log.warn("FEEDBACK_THROTTLED: compte {}", author.getId());
            return;
        }

        FeedbackEntity feedback = this.repository.saveAndFlush(FeedbackEntity.builder()
                .user(author)
                .message(form.getMessage().trim())
                .appVersion(Objects.isNull(form.getAppVersion())
                        ? null : form.getAppVersion().trim())
                .status(FeedbackStatus.PENDING)
                .build());

        log.info("FEEDBACK_RECEIVED: {} depuis la version {}",
                feedback.getId(), feedback.getAppVersion());
        this.notifyPlatform(feedback, author);
    }

    /**
     * Alerte adressée à YPYit, hors du chemin critique.
     *
     * <p>Le retour est déjà enregistré : un échec d'envoi le laisse relisible en base plutôt que
     * perdu.
     */
    private void notifyPlatform(FeedbackEntity feedback, UserEntity author) {
        try {
            Context context = new Context();
            context.setVariable("email", this.emailConfigProperties.getSupportEmail());
            context.setVariable("authorName",
                    String.join(" ", Objects.toString(author.getLastName(), ""),
                            Objects.toString(author.getFirstName(), "")).trim());
            context.setVariable("message", feedback.getMessage());
            context.setVariable("appVersion", feedback.getAppVersion());
            this.emailService.send(context, EmailTemplateType.FEEDBACK);
        } catch (Exception e) {
            log.error("FEEDBACK_MAIL_FAILED: {}", feedback.getId(), e);
        }
    }
}
