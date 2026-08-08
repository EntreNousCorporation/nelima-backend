package com.ypyit.neoelima.config.security;

import com.ypyit.neoelima.common.exception.UnAuthenticatedUserException;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.user.entity.AdminUserEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.service.impl.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Source de vérité du périmètre de données de l'appelant.
 *
 * <p>Aucun service ne doit faire confiance à un {@code establishmentId} reçu dans un formulaire
 * pour une lecture côté école : un utilisateur d'établissement authentifié pourrait alors lire
 * les données d'un autre établissement en changeant simplement le paramètre. Le périmètre est
 * dérivé ici de l'utilisateur authentifié.
 *
 * <p>Trois profils, trois portées :
 * <ul>
 *   <li>{@link AdminUserEntity} (YPYit) — peut cibler explicitement un établissement, ou tous ;</li>
 *   <li>{@link EstablishmentUserEntity} — épinglé à son établissement, le paramètre est ignoré ;</li>
 *   <li>parent — aucune portée établissement, l'accès se vérifie élève par élève.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UserDetailsServiceImpl userDetailsService;

    public UserEntity currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (Objects.isNull(authentication)
                || !authentication.isAuthenticated()
                || Objects.isNull(authentication.getName())) {
            throw new UnAuthenticatedUserException("No authenticated user in the security context");
        }
        return this.userDetailsService.loadUserByUsername(authentication.getName());
    }

    public boolean isPlatformAdmin() {
        return this.currentUser() instanceof AdminUserEntity;
    }

    public Optional<UUID> currentEstablishmentId() {
        return establishmentIdOf(this.currentUser());
    }

    /**
     * Vrai quand l'appelant lit au nom d'une école : personnel d'établissement ou admin YPYit.
     *
     * <p>Permet à un service de choisir son mode de lecture au lieu de dériver une portée
     * établissement à l'aveugle. Un parent n'a pas de périmètre école : lui appliquer
     * {@link #resolveEstablishmentScope(UUID)} lui refuserait l'accès à ses propres données.
     */
    public boolean hasEstablishmentScope() {
        UserEntity user = this.currentUser();
        return user instanceof AdminUserEntity || establishmentIdOf(user).isPresent();
    }

    /**
     * Portée établissement à appliquer à une requête de lecture côté école.
     *
     * @param requestedEstablishmentId valeur reçue du client, honorée uniquement pour un admin YPYit
     * @return l'établissement à filtrer ; {@code null} signifie « tous » et n'est possible que pour un admin
     */
    public UUID resolveEstablishmentScope(UUID requestedEstablishmentId) {
        UserEntity user = this.currentUser();
        if (user instanceof AdminUserEntity) {
            return requestedEstablishmentId;
        }
        return establishmentIdOf(user).orElseThrow(() -> new AccessDeniedException(
                "User is not attached to an establishment and cannot browse establishment data"));
    }

    /**
     * Vrai quand le rôle de l'appelant porte cette permission.
     *
     * <p>Les permissions arrivent comme autorités Spring depuis {@code UserEntity.getAuthorities()},
     * qui projette les codes portés par le rôle. Un admin YPYit passe toujours : ses accès se
     * jouent au niveau du filtre de sécurité, pas au niveau du rôle d'établissement.
     *
     * <p>Sert aux décisions qui ne se prennent pas à l'entrée du contrôleur — masquer un champ dans
     * une réponse plutôt que refuser l'appel entier. Pour refuser l'appel, préférer
     * {@code @PreAuthorize("hasAuthority('…')")}, qui le dit à la lecture de la signature.
     */
    public boolean hasPermission(String code) {
        UserEntity user = this.currentUser();
        if (user instanceof AdminUserEntity) {
            return true;
        }
        return user.getAuthorities().stream()
                .anyMatch(authority -> code.equals(authority.getAuthority()));
    }

    /**
     * Exige une permission, en laissant passer l'équipe YPYit.
     *
     * <p>À préférer à {@code @PreAuthorize} sur les routes que le back-office peut avoir à appeler :
     * le rôle {@code ADMIN} ne porte aucune permission — ses accès se jouent au niveau du filtre de
     * sécurité — et une annotation le refuserait sans que rien ne l'annonce.
     */
    public void assertPermission(String code) {
        if (!this.hasPermission(code)) {
            throw new AccessDeniedException("Permission " + code + " is required");
        }
    }

    /**
     * Vérifie que l'appelant a le droit d'agir sur cet établissement.
     *
     * <p>Un admin YPYit passe partout. Un utilisateur d'établissement ne passe que sur le sien. Un
     * parent ne passe nulle part : il n'administre aucune école.
     *
     * <p>Sans ce contrôle, l'identifiant reçu dans l'URL suffisait à écrire chez une autre école —
     * modifier son identité, y créer un compte, lire la liste de ses élèves. Le fait d'être
     * authentifié ne dit rien de l'école à laquelle on appartient.
     */
    public void assertCanAdministerEstablishment(UUID establishmentId) {
        UserEntity user = this.currentUser();
        if (user instanceof AdminUserEntity) {
            return;
        }
        UUID own = establishmentIdOf(user).orElseThrow(() -> new AccessDeniedException(
                "User is not attached to an establishment"));
        if (!own.equals(establishmentId)) {
            throw new AccessDeniedException(String.format(
                    "User is not allowed to administer establishment %s", establishmentId));
        }
    }

    /**
     * Vérifie que l'appelant agit bien sur son propre compte.
     *
     * <p>Un admin YPYit passe — ses accès se jouent au filtre de sécurité. Tout autre appelant ne
     * peut viser que le compte dont il détient le jeton : l'identifiant de l'URL doit être le sien.
     *
     * <p>Garde les deux routes qui portent un {@code {id}} de compte : la mise à jour du profil et
     * la lecture des enfants rattachés. Sans elle, l'identifiant reçu dans l'URL suffisait à écrire
     * le profil d'un autre — réécrire son contact principal, donc son identifiant de connexion, et
     * le verrouiller dehors — ou à lire ses enfants et les UUID de ses co-tuteurs. Le fait d'être
     * authentifié ne dit rien du compte que l'on a le droit de toucher.
     */
    public void assertCanManageUserAccount(UUID userId) {
        UserEntity user = this.currentUser();
        if (user instanceof AdminUserEntity) {
            return;
        }
        if (!Objects.equals(user.getId(), userId)) {
            throw new AccessDeniedException("User is not allowed to act on account " + userId);
        }
    }

    /**
     * Vérifie que l'appelant a le droit de consulter cet élève : admin YPYit, membre de
     * l'établissement de l'élève, ou tuteur rattaché.
     */
    public void assertCanAccessStudent(StudentEntity student) {
        UserEntity user = this.currentUser();
        if (user instanceof AdminUserEntity) {
            return;
        }
        Optional<UUID> callerEstablishment = establishmentIdOf(user);
        if (callerEstablishment.isPresent()
                && Objects.nonNull(student.getEstablishment())
                && callerEstablishment.get().equals(student.getEstablishment().getId())) {
            return;
        }
        boolean isGuardian = student.getParentUsers().stream()
                .anyMatch(parent -> parent.getId().equals(user.getId()));
        if (isGuardian) {
            return;
        }
        throw new AccessDeniedException("User is not allowed to access student " + student.getId());
    }

    private static Optional<UUID> establishmentIdOf(UserEntity user) {
        if (user instanceof EstablishmentUserEntity establishmentUser
                && Objects.nonNull(establishmentUser.getEstablishment())) {
            return Optional.of(establishmentUser.getEstablishment().getId());
        }
        return Optional.empty();
    }
}
