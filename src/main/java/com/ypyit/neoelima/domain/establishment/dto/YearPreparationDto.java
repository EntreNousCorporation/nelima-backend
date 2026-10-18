package com.ypyit.neoelima.domain.establishment.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** La rentrée d'une année dans l'école : les classes à ouvrir, puis les élèves à réinscrire. */
@Getter
@Builder
public class YearPreparationDto {
    private String targetYearId;
    private String targetLabel;
    /** Année d'où l'on vient ; nulle pour la toute première année de l'école. */
    private String previousYearId;
    private String previousLabel;
    private List<ClassLine> classes;
    /** Élèves de l'an passé pas encore réinscrits, par classe d'origine. */
    private List<Group> groups;

    @Getter
    @Builder
    public static class ClassLine {
        private String classId;
        private String name;
        private String levelCode;
        private String levelLabel;
        private boolean openedInPrevious;
        private boolean openedInTarget;
        private String previousTeacherName;
        private String targetTeacherName;
        private long enrolledInTarget;
    }

    @Getter
    @Builder
    public static class Group {
        /** Classe de l'an passé ; nulle pour les élèves qui n'en avaient pas. */
        private String sourceClassId;
        private String sourceClassName;
        private String levelLabel;
        /** Classe proposée pour la rentrée : une classe ouverte au niveau suivant. */
        private String suggestedTargetClassId;
        private List<StudentLine> students;
    }

    @Getter
    @Builder
    public static class StudentLine {
        private String studentId;
        private String name;
        private String registrationNumber;
    }
}
