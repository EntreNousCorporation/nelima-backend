package com.ypyit.neoelima.domain.establishment.controller;

import com.ypyit.neoelima.domain.establishment.dto.FeeScheduleDto;
import com.ypyit.neoelima.domain.establishment.form.FeeScheduleForm;
import com.ypyit.neoelima.domain.establishment.mapper.FeeScheduleMapper;
import com.ypyit.neoelima.domain.establishment.service.FeeScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Échéancier d'un frais, défini par l'établissement. */
@RestController
@RequestMapping("/fees/{feeId}/schedules")
@RequiredArgsConstructor
public class FeeScheduleController {

    private final FeeScheduleService feeScheduleService;
    private final FeeScheduleMapper feeScheduleMapper;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Échéancier du frais, dans l'ordre des versements")
    @PreAuthorize("hasAuthority('fee:read')")
    public ResponseEntity<List<FeeScheduleDto>> list(@PathVariable UUID feeId) {
        return ResponseEntity.ok(this.feeScheduleMapper.toDtos(this.feeScheduleService.findByFee(feeId)));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Remplace l'échéancier du frais",
            description = "La somme des tranches doit valoir le prix du frais. L'échéancier ne peut "
                    + "plus être modifié dès qu'une tranche a été encaissée. Les élèves déjà "
                    + "porteurs du frais reçoivent le nouvel échéancier.")
    @PreAuthorize("hasAuthority('fee:write')")
    public ResponseEntity<List<FeeScheduleDto>> define(@PathVariable UUID feeId,
                                                       @RequestBody @Valid List<FeeScheduleForm> schedules) {
        return ResponseEntity.ok(this.feeScheduleMapper
                .toDtos(this.feeScheduleService.defineSchedules(feeId, schedules)));
    }
}
