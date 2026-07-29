package com.ypyit.neoelima.domain.payment.controller;

import com.ypyit.neoelima.domain.payment.dto.PaymentInitiationDto;
import com.ypyit.neoelima.domain.payment.dto.ReceiptDto;
import com.ypyit.neoelima.domain.payment.form.OfflineCollectionForm;
import com.ypyit.neoelima.domain.payment.mapper.ReceiptMapper;
import com.ypyit.neoelima.domain.payment.service.OfflineCollectionService;
import com.ypyit.neoelima.domain.payment.service.OnlinePaymentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ypyit.neoelima.domain.payment.dto.PaymentChannelDto;
import com.ypyit.neoelima.domain.payment.service.PaymentChannelCatalogue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping(value = "/channels", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Canaux de paiement mobile proposés au parent",
            description = "Liste servie au choix d'opérateur dans l'application. Le code est "
                    + "transmis tel quel à l'agrégateur lors de l'initiation.")
    public ResponseEntity<List<PaymentChannelDto>> channels() {
        return ResponseEntity.ok(this.paymentChannelCatalogue.available());
    }

    @PostMapping(value = "/offline", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Enregistre un encaissement au guichet",
            description = "Espèces, chèque ou virement reçus par l'école. Solde la tranche et émet "
                    + "le reçu numéroté. Aucune commission n'est prélevée sur ce canal.")
    public ResponseEntity<ReceiptDto> collectOffline(@RequestBody @Valid OfflineCollectionForm form) {
        return ResponseEntity.ok(this.receiptMapper.toDto(this.offlineCollectionService.collect(form)));
    }
}
