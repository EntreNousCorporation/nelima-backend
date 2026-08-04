package com.ypyit.neoelima.domain.payment.controller;

import com.ypyit.neoelima.domain.payment.dto.ReceiptDto;
import com.ypyit.neoelima.domain.payment.enums.PaymentChannel;
import com.ypyit.neoelima.domain.payment.service.ReceiptCsvExporter;
import com.ypyit.neoelima.domain.payment.mapper.ReceiptMapper;
import com.ypyit.neoelima.domain.payment.service.ReceiptPdfRenderer;
import com.ypyit.neoelima.domain.payment.service.ReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;
    private final ReceiptMapper receiptMapper;
    private final ReceiptPdfRenderer receiptPdfRenderer;
    private final ReceiptCsvExporter receiptCsvExporter;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reçus émis par l'établissement, du plus récent au plus ancien")
    public ResponseEntity<Page<ReceiptDto>> search(
            @RequestParam(required = false) Instant issuedFrom,
            @RequestParam(required = false) Instant issuedTo,
            @RequestParam(required = false) PaymentChannel channel,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID studentId,
            @PageableDefault(size = 50, sort = "sequenceNumber", direction = Sort.Direction.DESC)
            @ParameterObject Pageable pageable) {
        var result = this.receiptService.search(null, issuedFrom, issuedTo, channel, keyword, studentId, pageable);
        return ResponseEntity.ok(new PageImpl<>(
                this.receiptMapper.toDtos(result.getContent()), pageable, result.getTotalElements()));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Journal des encaissements au format tableur",
            description = "Mêmes filtres que la liste. Le fichier est destiné au comptable de "
                    + "l'école : séparateur point-virgule et marque d'ordre d'octets, pour "
                    + "qu'Excel l'ouvre en colonnes et avec les accents.")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Instant issuedFrom,
            @RequestParam(required = false) Instant issuedTo,
            @RequestParam(required = false) PaymentChannel channel,
            @RequestParam(required = false) String keyword) {
        // Une page large plutôt que la pagination de l'écran : un export tronqué à vingt-cinq
        // lignes serait faux sans le dire, ce qui est le pire des défauts pour une pièce comptable.
        var result = this.receiptService.search(null, issuedFrom, issuedTo, channel, keyword, null,
                PageRequest.of(0, 10_000, Sort.by(Sort.Direction.DESC, "sequenceNumber")));
        byte[] csv = this.receiptCsvExporter.export(result.getContent());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("encaissements.csv").build().toString())
                .body(csv);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReceiptDto> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(this.receiptMapper.toDto(this.receiptService.findById(id)));
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Télécharge le reçu au format PDF",
            description = "Le document est régénéré à la demande depuis les données figées sur "
                    + "le reçu : rien n'est stocké, et un reçu déjà émis rend toujours le même PDF.")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
        var receipt = this.receiptService.findById(id);
        byte[] pdf = this.receiptPdfRenderer.render(receipt);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(this.receiptPdfRenderer.fileNameOf(receipt))
                                .build().toString())
                .body(pdf);
    }
}
