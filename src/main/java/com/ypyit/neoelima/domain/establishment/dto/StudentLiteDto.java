package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class StudentLiteDto extends BaseDto {

    private String firstName;
    private String lastName;
    private String registrationNumber;
    private LocalDate birthDay;
    private LevelOfStudyDto levelOfStudy;
    private String placeOfBirth;
}
