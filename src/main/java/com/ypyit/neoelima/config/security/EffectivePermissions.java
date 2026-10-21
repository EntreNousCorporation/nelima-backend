package com.ypyit.neoelima.config.security;

import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRoleRepository;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.PermissionEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Les permissions dont un compte dispose réellement : celles que son école a réglées pour son
 * rôle, ou, à défaut, celles du rôle sur la plateforme.
 *
 * <p>Source unique pour les trois lectures — annotations de sécurité ({@code AuthService}),
 * contrôles des services ({@link CurrentUserProvider#hasPermission}) et profil envoyé au portail :
 * qu'elles divergent, et un écran proposerait une action que le serveur refuse.
 */
@Component
@RequiredArgsConstructor
public class EffectivePermissions {

    /** Le rôle qui ne se règle pas : l'administrateur d'établissement garde toujours tout. */
    public static final String LOCKED_ROLE = "ESTABLISHMENT_ROOT";

    private final EstablishmentRoleRepository establishmentRoleRepository;

    public Set<String> of(UserEntity user) {
        if (Objects.isNull(user) || Objects.isNull(user.getRole())) {
            return Set.of();
        }
        UserEntity concrete = Hibernate.unproxy(user, UserEntity.class);
        if (concrete instanceof EstablishmentUserEntity schoolUser
                && Objects.nonNull(schoolUser.getEstablishment())
                && !LOCKED_ROLE.equals(user.getRole().getCode())) {
            var custom = this.establishmentRoleRepository
                    .findByEstablishment_IdAndRole_Id(schoolUser.getEstablishment().getId(), user.getRole().getId());
            if (custom.isPresent()) {
                return custom.get().getPermissions().stream().map(PermissionEntity::getCode).collect(Collectors.toSet());
            }
        }
        return user.getRole().getPermissions().stream().map(PermissionEntity::getCode).collect(Collectors.toSet());
    }

    public List<SimpleGrantedAuthority> authoritiesOf(UserEntity user) {
        return this.of(user).stream().map(SimpleGrantedAuthority::new).toList();
    }
}
