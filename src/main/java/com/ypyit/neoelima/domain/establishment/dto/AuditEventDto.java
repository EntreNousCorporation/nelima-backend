package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.domain.establishment.enums.AuditAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventDto {

    private String id;
    private AuditAction action;
    private Instant occurredAt;

    /** Identifiant de l'auteur, absent si le compte a disparu ; le nom, lui, reste. */
    private String actorId;
    private String actorName;

    private String target;
    private String details;
}
