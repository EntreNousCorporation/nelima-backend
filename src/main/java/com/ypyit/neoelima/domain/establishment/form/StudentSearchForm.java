package com.ypyit.neoelima.domain.establishment.form;

import com.ypyit.neoelima.common.validator.ContactValueCheck;
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
@ContactValueCheck
@NoArgsConstructor
@AllArgsConstructor
public class StudentSearchForm {

    private Set<String> levelOfStudies;
    private UUID establishmentId;
}
