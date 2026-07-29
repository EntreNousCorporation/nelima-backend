package com.ypyit.neoelima.domain.transverse.controller;

import com.ypyit.neoelima.domain.transverse.dto.GlobalParameterDto;
import com.ypyit.neoelima.domain.transverse.form.GlobalParameterUpdateForm;
import com.ypyit.neoelima.domain.transverse.service.GlobalParameterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@CrossOrigin("*")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/global-parameters")
public class GlobalParameterController {

    private final GlobalParameterService globalParameterService;

    @PutMapping(value = "/{code}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GlobalParameterDto> update(@RequestBody @Valid final GlobalParameterUpdateForm updateForm, @PathVariable final String code) {
        return ResponseEntity.ok(this.globalParameterService.update(code, updateForm));
    }

    @GetMapping(value = "/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GlobalParameterDto> findByCode(@PathVariable String code) {
        return ResponseEntity.ok(this.globalParameterService.findByCode(code));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<GlobalParameterDto>> getAll(@PageableDefault @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(this.globalParameterService.getAll(pageable));
    }
}
