package com.ypyit.neoelima.common.service.email.service.impl;


import com.ypyit.neoelima.common.service.email.enums.EmailTemplateType;
import com.ypyit.neoelima.common.service.email.service.EmailConstants;
import com.ypyit.neoelima.common.service.email.service.EmailService;
import com.ypyit.neoelima.config.audit.CurrentLocale;
import com.ypyit.neoelima.config.properties.EmailConfigProperties;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;


@Slf4j
@Async
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final MailProperties mailProperties;
    private final TemplateEngine templateEngine;
    private final MessageSource messageSource;
    private final EmailConfigProperties emailConfigProperties;

    @Override
    public void send(@NonNull Context context, @NonNull EmailTemplateType type) {
        Locale locale = CurrentLocale.getValue();
        String email = (String) context.getVariable(EmailConstants.EMAIL);
        log.info("Send email to " + email + " with locale " + locale.getLanguage());
        String template = type.getValue().concat("_" + locale.getLanguage());
        if (StringUtils.isBlank(email)) {
            log.error("User email is empty {} ", email);
            return;
        }
        if (!this.isTemplateExistsByName(template)) {
            log.error("Template email not exists {} ", template);
            return;
        }
        try {
            JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
            this.setMailSenderProperties(javaMailSender);
            javaMailSender.setJavaMailProperties(this.getMailProperties());

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper msgHelper = new MimeMessageHelper(message, false);

            String platformName = (String) context.getVariable(EmailConstants.PLATFORM_NAME);

            InternetAddress address = new InternetAddress(this.emailConfigProperties.getSupportEmail(),
                    this.emailConfigProperties.getEmailFrom());

            msgHelper.setFrom(address);
            msgHelper.setReplyTo(address);
            msgHelper.setTo(new InternetAddress(email));
            msgHelper.setSubject(this.messageSource.getMessage(type.getValue(), new Object[]{platformName}, locale));

            context.setVariable(EmailConstants.DATE, LocalDateTime.now(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            String body = this.templateEngine.process(template, context);
            msgHelper.setText(body, true);
            javaMailSender.send(message);
            log.info("Successfully send email to {}", email);

        } catch (Exception e) {
            log.error("Error while sending email ", e);
        }
    }

    private void setMailSenderProperties(JavaMailSenderImpl javaMailSender) {
        javaMailSender.setHost(this.mailProperties.getHost());
        javaMailSender.setPort(this.mailProperties.getPort());
        javaMailSender.setUsername(this.mailProperties.getUsername());
        javaMailSender.setPassword(this.mailProperties.getPassword());
    }

    private Properties getMailProperties() {
        Properties properties = new Properties();
        properties.setProperty("mail.transport.protocol", this.mailProperties.getProtocol());
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.starttls.required", "true");
        properties.setProperty("mail.smtp.ssl.trust", this.mailProperties.getHost());
        properties.setProperty("mail.smtp.timeout", "300000");

        return properties;
    }

    private boolean isTemplateExistsByName(String templateName) {
        if (StringUtils.isBlank(templateName)) {
            return false;
        }

        File file = null;
        String fileName = templateName.concat(EmailConstants.TEMPLATE_EXTENSION);
        Resource resource = new ClassPathResource("templates/" + fileName);
        try (InputStream inputStream = resource.getInputStream()) {
            file = new File(Paths.get(EmailConstants.TMP_DIR, fileName).toString());
            FileUtils.copyInputStreamToFile(inputStream, file);
            return file.exists() && file.isFile();
        } catch (Exception e) {
            log.error("Error while finding email template ", e);
            return false;
        } finally {
            log.info("Template file has been deleted {}", FileUtils.deleteQuietly(file));
        }
    }
}
