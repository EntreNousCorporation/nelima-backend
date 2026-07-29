package com.ypyit.neoelima.domain.transverse.controller;

import com.ypyit.neoelima.domain.establishment.dto.LevelOfStudyDto;
import com.ypyit.neoelima.domain.establishment.service.LevelOfStudyService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@CrossOrigin("*")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/level-of-studies")
public class LevelOfStudyController {

    private final LevelOfStudyService levelOfStudyService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<LevelOfStudyDto>> getAll(@PageableDefault(sort = {"position"}) @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(this.levelOfStudyService.getAll(pageable));
    }
}
