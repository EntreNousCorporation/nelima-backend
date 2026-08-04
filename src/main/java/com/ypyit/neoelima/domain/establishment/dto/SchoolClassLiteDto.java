package com.ypyit.neoelima.domain.establishment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Classe vue depuis un élève : de quoi l'afficher, sans traîner ses agrégats. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchoolClassLiteDto {

    private String id;
    private String name;
    private String room;
}
