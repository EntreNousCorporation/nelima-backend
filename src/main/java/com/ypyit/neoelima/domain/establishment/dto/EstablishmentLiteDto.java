package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class EstablishmentLiteDto extends BaseDto {

    private UUID id;
    private String name;
    private String webSite;
    private boolean active;
    private String logo;
    @Builder.Default
    private Set<EstablishmentLiteDto> subsidiaries = new HashSet<>();
    @Builder.Default
    private Set<LevelOfStudyDto> levelOfStudies = new HashSet<>();
}
