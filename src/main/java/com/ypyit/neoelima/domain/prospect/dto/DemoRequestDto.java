package com.ypyit.neoelima.domain.prospect.dto;

import com.ypyit.neoelima.domain.prospect.entity.DemoRequestStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Demande de démonstration, telle que la console la lit. Jamais rendue au site public. */
@Getter
@Setter
@Builder
public class DemoRequestDto {

    private UUID id;
    private String schoolName;
    private String contactName;
    private String email;
    private String phone;
    private String city;
    private Integer studentCount;
    private String message;
    private DemoRequestStatus status;
    private Instant createdAt;
    private Instant handledAt;
    private String handledNote;
    private String sourcePage;

    /** Formule que l'effectif déclaré appellerait. Prépare l'échange, n'engage rien. */
    private String suggestedPlan;
}
