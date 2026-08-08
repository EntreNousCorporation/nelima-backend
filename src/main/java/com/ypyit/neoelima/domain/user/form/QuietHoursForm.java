package com.ypyit.neoelima.domain.user.form;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Les bornes du silence, ou leur levée.
 *
 * <p>Les deux champs nuls lèvent les heures calmes. Les deux renseignés les posent. Un seul des
 * deux est refusé — une fenêtre sans fin n'est pas une préférence, c'est une panne.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuietHoursForm {

    @JsonFormat(pattern = "HH:mm")
    private LocalTime quietFrom;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime quietTo;
}
