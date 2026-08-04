package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.domain.establishment.enums.EducationCycle;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

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
    private String mainTeacherName;
    private String levelCode;
    private String levelLabel;
    private EducationCycle cycle;

    private long studentCount;

    /** Reste dû par les élèves de la classe, échéances passées et à venir confondues. */
    private BigDecimal outstandingAmount;

    /** Déjà encaissé auprès de ces mêmes élèves, depuis toujours. */
    private BigDecimal collectedAmount;
}
