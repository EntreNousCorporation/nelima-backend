package com.ypyit.neoelima.common.service.sms.service;

import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.common.service.sms.form.TokenResponse;
import org.springframework.scheduling.annotation.Async;

public abstract class SmsService {

    public abstract TokenResponse getAccessToken() throws BusinessException;

    protected abstract void sendMessage(final String message, final String phoneNumber,
                                        final String token) throws BusinessException;

    @Async
    public void sendTextMessage(final String message, final String phoneNumber) throws BusinessException {
        final TokenResponse token = getAccessToken();
        sendMessage(message, phoneNumber, token.getAccessToken());
    }
}
