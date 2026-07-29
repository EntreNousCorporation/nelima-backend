package com.ypyit.neoelima.common.advice.error;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Getter
@Setter
@Builder
public class ApiError {

    private HttpStatus status;
    private String debugMessage;
    private String path;
    private List<ErrorDetail> errors;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy'T'HH:mm:ss", timezone = "UTC")
    private LocalDateTime timestamp;

    public LocalDateTime getTimestamp() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
