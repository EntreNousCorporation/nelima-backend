package com.ypyit.neoelima.domain.user.enums;

public enum RoleType {

    ADMIN,

    /**
     * Compte d'amorçage d'une école, créé avec l'établissement.
     *
     * <p>Il cumule toutes les permissions : c'est lui qui existait avant les rôles fins, et le
     * dégrader ferait perdre des accès à des comptes en service.
     */
    ESTABLISHMENT_ROOT,

    DIRECTEUR,

    COMPTABLE,

    SECRETARIAT,

    STUDENT_PARENT
}
