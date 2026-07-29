package com.ypyit.neoelima.common.service.email.service;

import com.ypyit.neoelima.common.service.email.enums.EmailTemplateType;
import lombok.NonNull;
import org.thymeleaf.context.Context;

public interface EmailService {

    void send(@NonNull Context context, @NonNull EmailTemplateType type);

    /**
     * Envoi avec une pièce jointe unique.
     *
     * <p>Séparé de {@link #send} parce qu'un message porteur d'une pièce jointe doit être
     * construit en multipart, ce que l'envoi simple ne fait pas.
     */
    void sendWithAttachment(@NonNull Context context, @NonNull EmailTemplateType type,
                            @NonNull String fileName, @NonNull byte[] content,
                            @NonNull String contentType);
}
