package com.ypyit.neoelima.domain.user.form;

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
public class TenantCreationForm {

    private String name;
    private UUID companyId;
    private String description;
    private String rib;
    private String callBackUrl;
}
