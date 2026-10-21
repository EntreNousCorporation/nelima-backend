package com.ypyit.neoelima.domain.establishment.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Set;

/** Habilitations d'une école : ses rôles d'équipe et le catalogue des permissions. */
@Getter
@Builder
public class RoleSettingsDto {

    private List<Role> roles;
    private List<Permission> permissions;

    @Getter
    @Builder
    public static class Role {
        private String code;
        private String name;
        /** L'administrateur d'établissement : toujours tout, non réglable. */
        private boolean locked;
        /** Réglé par l'école ; sinon permissions de la plateforme. */
        private boolean customized;
        private Set<String> permissions;
        /** Permissions de la plateforme, pour « Rétablir les valeurs par défaut ». */
        private Set<String> defaults;
    }

    @Getter
    @Builder
    public static class Permission {
        private String code;
        private String label;
        /** Ce que la permission suppose : cochée, ces permissions le sont d'office. */
        private List<String> implies;
    }
}
