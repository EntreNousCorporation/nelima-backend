package com.ypyit.neoelima.common.service.sms.service.impl;

import com.ypyit.neoelima.common.service.sms.service.SmsSender;
import com.ypyit.neoelima.common.service.sms.service.SmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Mise en œuvre de {@link SmsSender} sur l'API Orange déjà en place.
 *
 * <p>Simple adaptateur : il ne fait qu'offrir une interface là où le code existant expose une
 * classe abstraite. Rien de la logique d'envoi n'est déplacé.
 */
@Service
@RequiredArgsConstructor
public class OrangeSmsSender implements SmsSender {

    private final SmsService smsService;

    @Override
    public void send(String message, String phoneNumber) {
        this.smsService.sendTextMessage(message, phoneNumber);
    }
}
