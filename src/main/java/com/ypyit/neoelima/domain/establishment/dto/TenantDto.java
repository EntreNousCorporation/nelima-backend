package com.ypyit.neoelima.domain.establishment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TenantDto {

    private UUID id;

    private String name;

    private String email;

    private String description;

    private String callBackUrl;

    private String apiKey;

    private UUID businessId;

    private UUID companyId;
}
