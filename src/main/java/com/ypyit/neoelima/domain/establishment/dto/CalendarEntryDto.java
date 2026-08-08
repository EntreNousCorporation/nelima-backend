package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.domain.establishment.enums.CalendarEntryKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Une entrée du calendrier, quelle que soit son origine.
 *
 * <p>Deux natures s'y mêlent : un événement saisi par l'école, et une échéance déduite des tranches
 * dues. L'écran les affiche de la même façon ; l'identifiant permet de rouvrir l'un ou de suivre
 * l'autre.
 *
 * <p>{@code amountExpected} et {@code amountCollected} ne sont renseignés que pour un appelant
 * portant {@code fee:read}. Un calendrier reste lisible sans les montants — il perd une colonne,
 * pas sa raison d'être.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEntryDto {

    /** Identifiant de l'événement ; pour une échéance, celui du frais concerné. */
    private String id;

    private CalendarEntryKind kind;
    private String title;
    private LocalDate date;
    private boolean allDay;
    private LocalTime startTime;
    private LocalTime endTime;
    private String details;

    /** Ce que l'entrée concerne : « Tout l'établissement », ou la liste des classes. */
    private String scope;

    /**
     * École dont vient l'entrée.
     *
     * <p>Inutile au portail, qui ne regarde jamais qu'un établissement à la fois. Indispensable à
     * l'application parent : un parent dont les enfants sont dans deux écoles reçoit un fil mêlé,
     * et sans ce champ il ne peut ni le trier ni savoir qui annonce quoi.
     */
    private String establishmentId;
    private String establishmentName;

    @Builder.Default
    private List<SchoolClassLiteDto> classes = new ArrayList<>();

    private boolean wholeSchool;

    /* ---- Événement saisi ---- */
    private Boolean visibleToFamilies;
    private Instant lastNotifiedAt;

    /* ---- Échéance déduite ---- */
    private BigDecimal amountExpected;
    private BigDecimal amountCollected;

    /** Nombre d'élèves concernés par l'échéance, et nombre de ceux qui l'ont réglée. */
    private Long studentsConcerned;
    private Long studentsSettled;
}
