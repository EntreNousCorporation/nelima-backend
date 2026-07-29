package com.ypyit.neoelima.domain.establishment.form;

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
public class StudentFeeSearchForm {

    private UUID studentId;
    private Boolean academical;
    private UUID feeId;
}
