package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;

import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le logo d'une école, sous les deux formes dont on a besoin.
 *
 * <ul>
 *   <li>Son adresse publique ({@code storage.nelima.ci}), pour les e-mails et les écrans : les
 *   messageries bloquent les images intégrées au message, pas un lien public.</li>
 *   <li>L'image elle-même, intégrée en {@code data:}, pour les PDF : le rendu se fait sur le serveur,
 *   il ne doit dépendre ni du réseau ni du DNS public au moment de générer un reçu.</li>
 * </ul>
 *
 * <p>Les images intégrées sont gardées en mémoire par adresse : un nouveau logo a une nouvelle
 * adresse, l'ancienne entrée n'est simplement plus demandée.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchoolLogo {

    private static final int CACHE_LIMIT = 200;

    private final StorageService storageService;
    private final Map<String, String> dataUris = new ConcurrentHashMap<>();

    /** Adresse publique du logo ; vide si l'école n'en a pas. */
    public Optional<String> publicUrl(EstablishmentEntity establishment) {
        if (Objects.isNull(establishment)) {
            return Optional.empty();
        }
        EstablishmentEntity school = Hibernate.unproxy(establishment, EstablishmentEntity.class);
        if (Objects.isNull(school.getCoverImage())) {
            return Optional.empty();
        }
        return Optional.ofNullable(StringUtils.trimToNull(school.getCoverImage().getLink()));
    }

    /** L'image intégrée ({@code data:image/png;base64,…}) ; vide si absente ou illisible. */
    public Optional<String> dataUri(EstablishmentEntity establishment) {
        return this.publicUrl(establishment).map(url -> this.dataUris.computeIfAbsent(url, this::load)).filter(StringUtils::isNotBlank);
    }

    /** Pose le nom de l'école et l'adresse de son logo dans un contexte d'e-mail. */
    public void addTo(Context context, EstablishmentEntity establishment) {
        if (Objects.isNull(establishment)) {
            return;
        }
        context.setVariable("establishmentName", establishment.getName());
        context.setVariable("schoolLogoUrl", this.publicUrl(establishment).orElse(null));
    }

    private String load(String url) {
        if (this.dataUris.size() >= CACHE_LIMIT) {
            this.dataUris.clear();
        }
        try {
            // Adresse publique « https://storage.nelima.ci/<bucket>/<objet> » : on relit le fichier
            // par le stockage lui-même, sans repasser par Internet.
            String path = URI.create(url).getPath();
            String[] parts = StringUtils.removeStart(path, "/").split("/", 2);
            if (parts.length < 2) {
                return "";
            }
            return this.storageService.read(parts[0], parts[1])
                    .map(bytes -> "data:" + mimeOf(parts[1]) + ";base64," + Base64.getEncoder().encodeToString(bytes))
                    .orElse("");
        } catch (RuntimeException e) {
            log.warn("SCHOOL_LOGO_UNREADABLE: {} : {}", url, e.getMessage());
            return "";
        }
    }

    private static String mimeOf(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/png";
    }
}
