package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.domain.user.dto.TranslateDto;
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
public class LevelOfStudyDto {

    private UUID id;
    private String code;
    private int position;
    private String previous;
    private String next;
    private TranslateDto name;
}
