package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.config.security.EffectivePermissions;
import com.ypyit.neoelima.domain.establishment.dto.RoleSettingsDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentRoleEntity;
import com.ypyit.neoelima.domain.establishment.enums.AuditAction;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRoleRepository;
import com.ypyit.neoelima.domain.user.entity.PermissionEntity;
import com.ypyit.neoelima.domain.user.entity.RoleEntity;
import com.ypyit.neoelima.domain.user.repository.PermissionRepository;
import com.ypyit.neoelima.domain.user.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Habilitations d'une école : ce que chaque rôle d'équipe peut voir et faire chez elle.
 *
 * <p>Les rôles réglables sont ceux de l'équipe — directeur, comptable, secrétariat. L'administrateur
 * d'établissement garde toujours tout : c'est lui qui règle les autres, et le priver de ce droit
 * fermerait l'école à elle-même. Sans réglage, un rôle garde les permissions de la plateforme.
 *
 * <p>Modifier implique de consulter : cocher « modifier les élèves » coche « consulter les élèves »,
 * faute de quoi l'écran s'ouvrirait sur une liste vide qu'on aurait pourtant le droit de remplir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleSettingsService {

    public static final String MANAGE = "user_access:write";

    /** Rôles d'équipe réglables, dans l'ordre de l'écran. */
    private static final List<String> EDITABLE = List.of("DIRECTEUR", "COMPTABLE", "SECRETARIAT");

    /** Une permission en suppose d'autres : on les ajoute d'office. */
    private static final Map<String, List<String>> IMPLIES = Map.ofEntries(
            Map.entry("staff:write", List.of("staff:read")),
            Map.entry("staff:read_salary", List.of("staff:read")),
            Map.entry("attendance:write", List.of("staff:read")),
            Map.entry("user_access:write", List.of("staff:read")),
            Map.entry("student:write", List.of("student:read")),
            Map.entry("class:write", List.of("class:read")),
            Map.entry("fee:write", List.of("fee:read")),
            Map.entry("collection:write", List.of("student:read")),
            Map.entry("reminder:write", List.of("accounting:read", "fee:read")),
            Map.entry("activity:write", List.of("activity:read")),
            Map.entry("calendar:write", List.of("calendar:read")),
            Map.entry("settings:write", List.of("settings:read")),
            Map.entry("canteen:write", List.of("canteen:read")),
            Map.entry("transport:write", List.of("transport:read")),
            Map.entry("writeoff:propose", List.of("fee:read")),
            Map.entry("writeoff:approve", List.of("fee:read")));

    private final EstablishmentRoleRepository establishmentRoleRepository;
    private final EstablishmentRepository establishmentRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;

    public RoleSettingsDto settings() {
        UUID scope = this.scope();
        Map<UUID, EstablishmentRoleEntity> custom = this.establishmentRoleRepository.findByEstablishment_Id(scope).stream()
                .collect(Collectors.toMap(c -> c.getRole().getId(), c -> c));
        List<RoleSettingsDto.Role> roles = new java.util.ArrayList<>();
        this.roleRepository.findByCode(EffectivePermissions.LOCKED_ROLE).ifPresent(root -> roles.add(RoleSettingsDto.Role.builder()
                .code(root.getCode()).name(nameOf(root)).locked(true).customized(false)
                .permissions(codes(root.getPermissions())).build()));
        for (String code : EDITABLE) {
            this.roleRepository.findByCode(code).ifPresent(role -> {
                EstablishmentRoleEntity own = custom.get(role.getId());
                roles.add(RoleSettingsDto.Role.builder()
                        .code(role.getCode()).name(nameOf(role)).locked(false)
                        .customized(Objects.nonNull(own))
                        .permissions(codes(Objects.nonNull(own) ? own.getPermissions() : role.getPermissions()))
                        .defaults(codes(role.getPermissions()))
                        .build());
            });
        }
        List<RoleSettingsDto.Permission> catalogue = this.permissionRepository.findAll().stream()
                .sorted(Comparator.comparing(PermissionEntity::getCode))
                .map(p -> RoleSettingsDto.Permission.builder()
                        .code(p.getCode())
                        .label(Objects.isNull(p.getName()) ? p.getCode() : Objects.toString(p.getName().getFr(), p.getCode()))
                        .implies(IMPLIES.getOrDefault(p.getCode(), List.of()))
                        .build())
                .toList();
        return RoleSettingsDto.builder().roles(roles).permissions(catalogue).build();
    }

    @Transactional
    public RoleSettingsDto update(String roleCode, Collection<String> permissionCodes) {
        this.currentUserProvider.assertPermission(MANAGE);
        UUID scope = this.scope();
        RoleEntity role = this.editableRole(roleCode);
        Set<String> wanted = withImplied(Objects.requireNonNullElse(permissionCodes, List.of()));
        Set<PermissionEntity> permissions = new java.util.HashSet<>();
        for (String code : wanted) {
            permissions.add(this.permissionRepository.findByCode(code)
                    .orElseThrow(() -> new BadRequestException("Permission inconnue : " + code)));
        }
        Optional<EstablishmentRoleEntity> existing = this.establishmentRoleRepository.findByEstablishment_IdAndRole_Id(scope, role.getId());
        Set<String> before = codes(existing.map(EstablishmentRoleEntity::getPermissions).orElse(role.getPermissions()));
        EstablishmentRoleEntity entity = existing.orElseGet(() -> EstablishmentRoleEntity.builder()
                .establishment(this.establishmentRepository.getReferenceById(scope)).role(role).build());
        entity.getPermissions().clear();
        entity.getPermissions().addAll(permissions);
        this.establishmentRoleRepository.saveAndFlush(entity);

        Set<String> added = new TreeSet<>(wanted);
        added.removeAll(before);
        Set<String> removed = new TreeSet<>(before);
        removed.removeAll(wanted);
        this.auditService.record(scope, AuditAction.ROLE_PERMISSIONS_CHANGED, nameOf(role),
                String.format("Ajouté : %s · Retiré : %s", added.isEmpty() ? "—" : String.join(", ", added),
                        removed.isEmpty() ? "—" : String.join(", ", removed)));
        log.info("ROLE_PERMISSIONS_CHANGED: {} in establishment {} (+{} -{})", role.getCode(), scope, added, removed);
        return this.settings();
    }

    /** Rend au rôle les permissions de la plateforme. */
    @Transactional
    public RoleSettingsDto reset(String roleCode) {
        this.currentUserProvider.assertPermission(MANAGE);
        UUID scope = this.scope();
        RoleEntity role = this.editableRole(roleCode);
        this.establishmentRoleRepository.findByEstablishment_IdAndRole_Id(scope, role.getId()).ifPresent(entity -> {
            this.establishmentRoleRepository.delete(entity);
            this.establishmentRoleRepository.flush();
            this.auditService.record(scope, AuditAction.ROLE_PERMISSIONS_CHANGED, nameOf(role), "Permissions par défaut rétablies");
        });
        return this.settings();
    }

    /** Ajoute ce que chaque permission suppose, jusqu'à stabilité. */
    static Set<String> withImplied(Collection<String> codes) {
        Set<String> result = new LinkedHashSet<>(codes);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (String code : List.copyOf(result)) {
                for (String implied : IMPLIES.getOrDefault(code, List.of())) {
                    grew |= result.add(implied);
                }
            }
        }
        return result;
    }

    private RoleEntity editableRole(String code) {
        if (!EDITABLE.contains(code)) {
            throw new BadRequestException("Ce rôle ne se règle pas depuis l'école.");
        }
        return this.roleRepository.findByCode(code).orElseThrow(() -> new NotFoundException("Rôle introuvable."));
    }

    private static Set<String> codes(Collection<PermissionEntity> permissions) {
        return permissions.stream().map(PermissionEntity::getCode).collect(Collectors.toCollection(TreeSet::new));
    }

    private static String nameOf(RoleEntity role) {
        return Objects.isNull(role.getName()) ? role.getCode() : Objects.toString(role.getName().getFr(), role.getCode());
    }

    private UUID scope() {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        return scope;
    }
}
