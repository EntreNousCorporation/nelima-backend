package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.common.dto.BaseDto;
import com.ypyit.neoelima.domain.user.dto.AddressDto;
import com.ypyit.neoelima.domain.user.dto.ContactDto;
import com.ypyit.neoelima.domain.user.dto.EstablishmentUserDto;
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
public class EstablishmentDto extends BaseDto {

    private UUID id;
    private String name;
    private String shortName;
    private String accreditationNumber;
    private String webSite;
    private String logo;
    private boolean isPrimary;
    private boolean active;
    private AddressDto address;
    private EstablishmentUserDto principal;
    private EstablishmentLiteDto parent;
    @Builder.Default
    private Set<ContactDto> contacts = new HashSet<>();
    @Builder.Default
    private Set<LevelOfStudyDto> levelOfStudies = new HashSet<>();
}
