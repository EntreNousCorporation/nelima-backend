package com.ypyit.neoelima.domain.establishment.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Getter
@Setter
@Builder
public class TimetableSlotDto {
    private String id;
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private String subject;
    private String teacherId;
    private String teacherName;
    private String room;
}
