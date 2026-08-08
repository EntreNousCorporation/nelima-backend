package com.ypyit.neoelima.domain.payment.controller;

import com.ypyit.neoelima.domain.payment.dto.PaymentInitiationDto;
import com.ypyit.neoelima.domain.payment.dto.PaymentIntentStatusDto;
import com.ypyit.neoelima.domain.payment.dto.PaymentQuoteDto;
import com.ypyit.neoelima.domain.payment.dto.ReceiptDto;
import com.ypyit.neoelima.domain.payment.form.OfflineCollectionForm;
import com.ypyit.neoelima.domain.payment.mapper.ReceiptMapper;
import com.ypyit.neoelima.domain.payment.dto.TransactionDto;
import com.ypyit.neoelima.domain.payment.dto.TransactionSummaryDto;
import com.ypyit.neoelima.domain.payment.service.OfflineCollectionService;
import com.ypyit.neoelima.domain.payment.service.TransactionService;
import com.ypyit.neoelima.domain.payment.service.OnlinePaymentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ypyit.neoelima.domain.payment.dto.PaymentChannelDto;
import com.ypyit.neoelima.domain.payment.service.PaymentChannelCatalogue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final OnlinePaymentService onlinePaymentService;
    private final OfflineCollectionService offlineCollectionService;
    private final ReceiptMapper receiptMapper;
    private final PaymentChannelCatalogue paymentChannelCatalogue;
    private final TransactionService transactionService;

    @PostMapping(value = "/installments/{installmentId}/online", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Démarre le règlement en ligne d'une tranche",
            description = "Renvoie l'URL du tunnel de paiement de l'agrégateur, à ouvrir dans une "
                    + "webview, ainsi que le détail des montants. Le parent est débité de la "
                    + "tranche augmentée de la commission YPYit. Tout utilisateur authentifié peut "
                    + "payer pour un élève, il n'a pas à en être le tuteur déclaré.")
    public ResponseEntity<PaymentInitiationDto> initiateOnline(
            @PathVariable UUID installmentId,
            @RequestParam(value = "channel", required = false) String channel) {
        return ResponseEntity.ok(this.onlinePaymentService.initiate(installmentId, channel));
    }

    @GetMapping(value = "/intents/{paymentIntentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Où en est une tentative de paiement",
            description = "Interrogée par l'application pendant que le tunnel de l'agrégateur est "
                    + "ouvert. `SUCCEEDED` n'est posé qu'une fois la tranche soldée et le reçu "
                    + "émis : c'est le seul état qui autorise à annoncer un paiement réussi à une "
                    + "famille. Réservée au payeur.")
    public ResponseEntity<PaymentIntentStatusDto> intentStatus(@PathVariable UUID paymentIntentId) {
        return ResponseEntity.ok(this.onlinePaymentService.statusOf(paymentIntentId));
    }

    @GetMapping(value = "/installments/{installmentId}/quote", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Détail du coût du règlement d'une tranche",
            description = "Montant de la tranche, commission YPYit et total débité, calculés par le "
                    + "même code que l'initiation. À afficher au parent avant qu'il ne valide : "
                    + "c'est ce qui garantit que la somme annoncée est celle qui sera prélevée. "
                    + "N'engage rien et ne crée aucune tentative de paiement.")
    public ResponseEntity<PaymentQuoteDto> quote(@PathVariable UUID installmentId) {
        return ResponseEntity.ok(this.onlinePaymentService.quote(installmentId));
    }

    @GetMapping(value = "/channels", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Canaux de paiement mobile proposés au parent",
            description = "Liste servie au choix d'opérateur dans l'application. Le code est "
                    + "transmis tel quel à l'agrégateur lors de l'initiation.")
    public ResponseEntity<List<PaymentChannelDto>> channels() {
        return ResponseEntity.ok(this.paymentChannelCatalogue.available());
    }

    @GetMapping(value = "/transactions", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('accounting:read')")
    @Operation(summary = "Tentatives de paiement de l'établissement, pour le rapprochement",
            description = "Une opération réussie sans reçu émis est à réconcilier : l'argent est "
                    + "arrivé mais la comptabilité de l'école est incomplète.")
    public ResponseEntity<List<TransactionDto>> transactions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(this.transactionService.findAll(from, to));
    }

    @GetMapping(value = "/transactions/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('accounting:read')")
    @Operation(summary = "Encaissé net, à réconcilier, en attente opérateur et frais collectés")
    public ResponseEntity<TransactionSummaryDto> transactionsSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(this.transactionService.summarize(from, to));
    }

    @PostMapping(value = "/offline", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Enregistre un encaissement au guichet",
            description = "Espèces, chèque ou virement reçus par l'école. Solde la tranche et émet "
                    + "le reçu numéroté. Aucune commission n'est prélevée sur ce canal.")
    @PreAuthorize("hasAuthority('collection:write')")
    public ResponseEntity<ReceiptDto> collectOffline(@RequestBody @Valid OfflineCollectionForm form) {
        return ResponseEntity.ok(this.receiptMapper.toDto(this.offlineCollectionService.collect(form)));
    }
}
