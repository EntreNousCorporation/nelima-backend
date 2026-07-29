package com.ypyit.neoelima.domain.establishment.form;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeSearchForm {

    private UUID establishmentId;
    private Set<String> levelOfStudies;
    private Boolean optional;
    private Boolean academical;
}
