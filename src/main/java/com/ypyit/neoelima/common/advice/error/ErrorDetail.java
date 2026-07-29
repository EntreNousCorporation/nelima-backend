package com.ypyit.neoelima.common.advice.error;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ErrorDetail {
    private String code;
    private String field;
    private String defaultMessage;
    private Object rejectValue;
}
