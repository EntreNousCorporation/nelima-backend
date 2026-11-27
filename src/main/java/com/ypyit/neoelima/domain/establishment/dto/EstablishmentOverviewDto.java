package com.ypyit.neoelima.domain.establishment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ce qui compose une école, vu par la console YPYit : ses comptes, son personnel, ses niveaux et
 * ses classes.
 *
 * <p>Ce que la fiche latérale ne pouvait pas tenir. L'avancement, l'abonnement et l'identité ont
 * déjà leurs routes ; celle-ci apporte les personnes et l'organisation pédagogique.
 *
 * <p>Les salaires du personnel n'y figurent pas : l'équipe plateforme accompagne la configuration,
 * elle n'a pas à lire la paie d'une école cliente.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstablishmentOverviewDto {

    /** Comptes de connexion au portail, le directeur en tête. */
    private List<Account> accounts;

    /** Fiches du personnel, actives d'abord. */
    private List<StaffMember> staff;

    /** Niveaux déclarés, dans l'ordre du cursus, chacun avec ses classes. */
    private List<Level> levels;

    /** Élèves sans classe : à répartir par l'école. */
    private long studentsWithoutClass;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Account {
        private UUID id;
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        /** Libellé du rôle : « Administrateur d'établissement »… */
        private String role;
        /** Le compte de direction de l'école, celui qui a reçu le lien de bienvenue. */
        private boolean principal;
        /** Mot de passe défini : le lien de bienvenue a servi. */
        private boolean activated;
        private Instant lastLogin;
        private boolean enabled;
        private boolean locked;
        /** Compte de groupe, qui voit toutes les écoles du groupe. */
        private boolean groupWide;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffMember {
        private UUID id;
        private String firstName;
        private String lastName;
        /** TEACHER, ADMINISTRATION ou DIRECTION. */
        private String role;
        private String jobTitle;
        private String phone;
        private String email;
        private String contractType;
        private LocalDate hiredAt;
        private boolean active;
        /** Un compte de connexion au portail lui est rattaché. */
        private boolean portalAccess;
        /** Classes dont il est professeur principal. */
        private List<String> mainTeacherOf;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Level {
        private String code;
        private String label;
        /** KINDERGARTEN, PRIMARY, SECONDARY… */
        private String cycle;
        private long students;
        private List<SchoolClass> classes;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SchoolClass {
        private UUID id;
        private String name;
        private Integer capacity;
        private String room;
        private String mainTeacher;
        private long students;
    }
}
