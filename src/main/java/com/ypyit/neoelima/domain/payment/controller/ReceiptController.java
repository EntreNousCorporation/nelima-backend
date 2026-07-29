package com.ypyit.neoelima.domain.payment.controller;

import com.ypyit.neoelima.domain.payment.dto.ReceiptDto;
import com.ypyit.neoelima.domain.payment.mapper.ReceiptMapper;
import com.ypyit.neoelima.domain.payment.service.ReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;
    private final ReceiptMapper receiptMapper;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reçus émis par l'établissement, du plus récent au plus ancien")
    public ResponseEntity<Page<ReceiptDto>> search(
            @PageableDefault(size = 50, sort = "sequenceNumber", direction = Sort.Direction.DESC)
            @ParameterObject Pageable pageable) {
        var result = this.receiptService.search(null, pageable);
        return ResponseEntity.ok(new PageImpl<>(
                this.receiptMapper.toDtos(result.getContent()), pageable, result.getTotalElements()));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReceiptDto> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(this.receiptMapper.toDto(this.receiptService.findById(id)));
    }
}
