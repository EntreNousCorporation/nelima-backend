package com.ypyit.neoelima.security;

import com.ypyit.neoelima.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les routes de l'application parent ne doivent exiger aucune permission.
 *
 * <p>Le rôle {@code STUDENT_PARENT} n'en porte aucune, et n'en portera pas : un parent n'a pas de
 * périmètre établissement, son accès se vérifie enfant par enfant. Poser un {@code @PreAuthorize}
 * sur l'une de ces routes lui répondrait 403 — l'application mobile n'aurait plus rien à afficher
 * et aucun paiement ne serait possible.
 *
 * <p>C'est déjà arrivé, par un autre chemin : la portée « établissement » avait été dérivée pour
 * tout appelant, et les tests côté école passaient tous. Ce cas-ci verrouille la version
 * « permissions » de la même erreur, et il le fait sur la structure plutôt que sur un appel : il
 * échoue dès qu'on ajoute l'annotation, sans qu'il faille penser à écrire le pendant parent.
 */
class ParentRouteOpennessTest extends AbstractIntegrationTest {

    /**
     * Routes servies à l'application parent.
     *
     * <p>Certaines servent aussi le portail école — les reçus, les échéances — et c'est justement
     * ce qui les rend fragiles : on les annote en pensant « écran école » sans voir l'autre
     * appelant.
     */
    private static final List<String> PARENT_ROUTES = List.of(
            "GET /students",           // recherche d'un enfant par matricule et date de naissance
            "GET /students/mine",
            // Le premier écran de l'application. Une permission ici et la famille ouvre le produit
            // sur un 403, avant même d'avoir pu rattacher un enfant.
            "GET /dashboard/parent",
            // Le fil de notifications sert les deux publics sur la même route : le contenu est
            // filtré dans le service, pas à l'entrée.
            "GET /notifications",
            "POST /notifications/seen",
            "POST /students/claim",
            "GET /students/{id}/fees",
            "GET /installments",
            "GET /student-fees",
            "GET /receipts",
            "GET /receipts/{id}",
            "GET /receipts/{id}/pdf",
            "GET /payments/channels",
            "GET /payments/installments/{installmentId}/quote",
            "POST /payments/installments/{installmentId}/online",
            // Sondée pendant que le tunnel de l'agrégateur est ouvert : une permission ici et
            // l'application ne saurait jamais si le règlement a abouti.
            "GET /payments/intents/{paymentIntentId}",
            "GET /level-of-studies",
            "GET /activities/open",
            "POST /activities/{id}/enrollments/mine",
            "DELETE /activities/{id}/enrollments/mine/{studentId}",
            "GET /calendar/mine",
            // L'application parent s'en sert pour rattacher un enfant à son école. La liste
            // n'expose qu'un annuaire — nom, site, logo — mais la fermer casserait le
            // rattachement, seul moyen pour une famille d'entrer dans le produit.
            "GET /establishments",
            // Coordonnées de l'école d'un de ses enfants. La portée est dérivée des enfants
            // rattachés dans le service ; une permission ici la fermerait à tous les parents.
            "GET /establishments/{id}/contact",
            // Réglages du compte appelant : il n'y a rien à autoriser au-delà d'être authentifié.
            "GET /users/me/notification-preferences",
            "PUT /users/me/notification-preferences",
            "PUT /users/me/quiet-hours",
            "POST /feedback");

    /**
     * Le mapping des contrôleurs applicatifs.
     *
     * <p>Nommé explicitement : Actuator en publie un second pour ses propres points d'entrée, et
     * l'injection par type ne saurait lequel prendre.
     */
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("aucune route de l'application parent n'exige de permission")
    void parentRoutesRequireNoPermission() {
        Set<String> guarded = new TreeSet<>();
        Set<String> guardedAnywhere = new TreeSet<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry
                : this.handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            boolean hasGuard = Objects.nonNull(handler.getMethodAnnotation(PreAuthorize.class))
                    || Objects.nonNull(handler.getBeanType().getAnnotation(PreAuthorize.class));
            if (!hasGuard) {
                continue;
            }
            for (String route : routesOf(entry.getKey())) {
                guardedAnywhere.add(route);
                if (PARENT_ROUTES.contains(route)) {
                    guarded.add(route);
                }
            }
        }

        // Sans ce garde-fou, le cas serait vide de sens : une erreur de lecture des annotations le
        // ferait passer en ne trouvant jamais rien, y compris le jour où une route parent en porte
        // vraiment une.
        assertThat(guardedAnywhere)
                .as("la lecture des annotations doit trouver les routes réservées à l'école")
                .contains("GET /staff", "POST /classes");

        assertThat(guarded)
                .as("ces routes servent l'application parent, dont le rôle ne porte aucune "
                        + "permission : les protéger ainsi lui répondrait 403")
                .isEmpty();
    }

    @Test
    @DisplayName("la liste des routes parent décrit des routes qui existent")
    void parentRoutesStillExist() {
        Set<String> declared = new TreeSet<>();
        for (RequestMappingInfo info : this.handlerMapping.getHandlerMethods().keySet()) {
            declared.addAll(routesOf(info));
        }

        // Sans ce contrôle, renommer une route viderait le cas précédent de sa substance : il
        // continuerait de passer en surveillant des chemins qui n'existent plus.
        assertThat(declared).containsAll(PARENT_ROUTES);
    }

    private static Set<String> routesOf(RequestMappingInfo info) {
        Set<String> routes = new TreeSet<>();
        Set<String> patterns = Objects.nonNull(info.getPathPatternsCondition())
                ? info.getPathPatternsCondition().getPatternValues()
                : Set.of();
        var methods = info.getMethodsCondition().getMethods();
        for (String pattern : patterns) {
            if (methods.isEmpty()) {
                routes.add("ANY " + pattern);
                continue;
            }
            methods.forEach(method -> routes.add(method.name() + " " + pattern));
        }
        return routes;
    }
}
