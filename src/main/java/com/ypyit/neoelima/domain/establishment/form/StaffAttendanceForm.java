package com.ypyit.neoelima.domain.establishment.form;

import com.ypyit.neoelima.domain.establishment.enums.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class StaffAttendanceForm {

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate day;

    @NotNull
    private AttendanceStatus status;

    @Size(max = 255)
    private String note;
}
