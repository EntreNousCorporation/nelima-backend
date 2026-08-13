package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.domain.establishment.enums.EducationCycle;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Classe telle que le portail l'affiche : ses attributs, son effectif réel, et où en sont les
 * familles qui la composent.
 *
 * <p>L'effectif et le recouvrement sont calculés en base et servis avec la classe : les demander
 * classe par classe depuis l'écran aurait fait une requête par ligne du tableau.
 */
@Getter
@Setter
@Builder
public class SchoolClassDto {

    private String id;
    private String name;
    private String room;
    private Integer capacity;
    /** Membre du personnel désigné titulaire ; nul tant qu'aucun ne l'est. */
    private String mainTeacherId;

    /**
     * Nom du titulaire, quelle qu'en soit la source.
     *
     * <p>Servi depuis la référence quand elle existe, depuis le nom hérité sinon. L'appelant n'a
     * pas à savoir laquelle des deux a répondu : il affiche un titulaire.
     */
    private String mainTeacherName;
    private String levelCode;
    private String levelLabel;
    private EducationCycle cycle;

    /**
     * Les enseignants rattachés à cette classe depuis l'écran Personnel.
     *
     * <p>Distinct du titulaire : une classe n'a qu'un titulaire et peut compter plusieurs
     * intervenants. Rien ne les montrait, si bien qu'une école qui rattachait ses enseignants
     * depuis Personnel ne voyait rien changer côté Classes — et concluait, à raison, que « ça n'a
     * pas le même effet ».
     */
    private List<String> teacherNames;

    private long studentCount;

    /** Reste dû par les élèves de la classe, échéances passées et à venir confondues. */
    private BigDecimal outstandingAmount;

    /** Déjà encaissé auprès de ces mêmes élèves, depuis toujours. */
    private BigDecimal collectedAmount;
}
