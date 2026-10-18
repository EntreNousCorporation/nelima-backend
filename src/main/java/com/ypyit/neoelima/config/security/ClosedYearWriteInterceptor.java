package com.ypyit.neoelima.config.security;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.UnAuthenticatedUserException;
import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import com.ypyit.neoelima.domain.establishment.service.AcademicYearContext;
import com.ypyit.neoelima.domain.establishment.service.SchoolYearGuard;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Lecture seule pour qui consulte une année clôturée.
 *
 * <p>Une seule porte pour toutes les écritures, plutôt qu'un contrôle par écran : un écran oublié
 * — et il y en aurait eu — rouvrirait l'année. Restent permis les gestes qui ne touchent pas aux
 * données de l'école : changer d'année, se déconnecter, son mot de passe, ses préférences.
 */
@Component
@RequiredArgsConstructor
public class ClosedYearWriteInterceptor implements HandlerInterceptor {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    /** Gestes personnels, sans rapport avec les données de l'année. */
    private static final List<String> ALLOWED_PREFIXES = List.of(
            "/auth/",
            "/academic-years/consulted",
            "/users/change-password",
            "/users/me/",
            "/notifications/seen");

    private final CurrentUserProvider currentUserProvider;
    private final AcademicYearContext academicYearContext;
    private final SchoolYearGuard schoolYearGuard;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (ALLOWED_PREFIXES.stream().anyMatch(path::startsWith)) {
            return true;
        }
        UserEntity user;
        try {
            user = this.currentUserProvider.currentUser();
        } catch (UnAuthenticatedUserException e) {
            return true;
        }
        // Seuls les comptes d'école consultent une année de leur école ; parents et plateforme
        // travaillent toujours dans l'année en cours, qui ne se clôture pas.
        if (!(user instanceof EstablishmentUserEntity schoolUser) || schoolUser.getEstablishment() == null) {
            return true;
        }
        Optional<AcademicYearEntity> consulted = this.academicYearContext.consulted();
        if (consulted.isPresent()
                && this.schoolYearGuard.isClosed(schoolUser.getEstablishment().getId(), consulted.get().getId())) {
            throw new BadRequestException(SchoolYearGuard.closedMessage(consulted.get().getLabel()));
        }
        return true;
    }
}
