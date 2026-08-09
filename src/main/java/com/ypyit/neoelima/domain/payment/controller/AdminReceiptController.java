package com.ypyit.neoelima.domain.payment.controller;

import com.ypyit.neoelima.domain.payment.dto.ReceiptDto;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Reçus vus par la console opérateur YPYit (back-office).
 *
 * <p>Distinct de {@link ReceiptController}, qui est scopé à l'école de l'appelant : ici l'opérateur
 * voit le parc — nominativement, chaque reçu portant son payeur ({@code payerLabel}). L'accès est
 * réservé à l'équipe plateforme au niveau du filtre de sécurité
 * ({@code SecurityUtils.PLATFORM_CONSOLE_RESOURCES}), comme {@code /admin/parents} : le rôle admin
 * ne porte aucune permission, une annotation {@code @PreAuthorize} le refuserait à tort.
 *
 * <p>Tri par date d'émission décroissante : le numéro de séquence est propre à chaque établissement
 * et n'ordonne rien à l'échelle du parc.
 */
@RestController
@RequiredArgsConstructor
public class AdminReceiptController {

    private final ReceiptService receiptService;
    private final ReceiptMapper receiptMapper;

    @GetMapping(value = "/admin/receipts", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reçus du parc (back-office YPYit), avec le payeur",
            description = "Filtre establishmentId optionnel. Réservé à l'équipe plateforme.")
    public ResponseEntity<Page<ReceiptDto>> list(
            @RequestParam(required = false) UUID establishmentId,
            @RequestParam(required = false) Instant issuedFrom,
            @RequestParam(required = false) Instant issuedTo,
            @RequestParam(required = false) PaymentChannel channel,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 50, sort = "issuedAt", direction = Sort.Direction.DESC)
            @ParameterObject Pageable pageable) {
        var result = this.receiptService.searchForPlatform(
                establishmentId, issuedFrom, issuedTo, channel, keyword, pageable);
        return ResponseEntity.ok(new PageImpl<>(
                this.receiptMapper.toDtos(result.getContent()), pageable, result.getTotalElements()));
    }
}
