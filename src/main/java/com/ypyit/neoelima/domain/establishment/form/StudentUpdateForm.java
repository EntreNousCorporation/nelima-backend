package com.ypyit.neoelima.domain.establishment.form;

import com.ypyit.neoelima.common.validator.NoXssContent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentUpdateForm {

    @NoXssContent
    private String firstName;
    @NoXssContent
    private String lastName;
    @NoXssContent
    private String registrationNumber;
    @NoXssContent
    private String placeOfBirth;
    private LocalDate birthDay;
    @NoXssContent
    private String levelOfStudyCode;
}
