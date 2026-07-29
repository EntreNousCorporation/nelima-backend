package com.ypyit.neoelima.common.service.sms.service.impl;

import com.ypyit.neoelima.common.cache.CacheService;
import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.common.service.sms.form.OutboundSMSMessageRequest;
import com.ypyit.neoelima.common.service.sms.form.OutboundSMSTextMessage;
import com.ypyit.neoelima.common.service.sms.form.SendRequest;
import com.ypyit.neoelima.common.service.sms.form.TokenResponse;
import com.ypyit.neoelima.common.service.sms.service.SmsService;
import com.ypyit.neoelima.config.properties.SmsProperties;
import com.ypyit.neoelima.domain.transverse.service.HttpClientService;
import com.ypyit.neoelima.domain.utils.HeaderUtils;
import com.ypyit.neoelima.domain.utils.RestClientUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SmsServiceImpl extends SmsService {

    private static final int TOKEN_TTL = 20;
    private static final String ADDRESS = "tel:%s";
    private static final String SENDER = "tel:+2250000";
    private static final String SMS_TOKEN_KEY = "SMS_TOKEN";
    private final SmsProperties smsProperties;
    private final CacheService cacheService;
    private final HttpClientService httpClientService;

    @Override
    public TokenResponse getAccessToken() throws BusinessException {
        try {
            Object token = this.cacheService.getValue(SMS_TOKEN_KEY);
            if (Objects.nonNull(token)) {
                return TokenResponse.builder()
                        .accessToken((String) token)
                        .build();
            }
            WebClient client = this.createTokenClient(this.getBasicAuthCreds(this.smsProperties.getUsername(),
                    this.smsProperties.getPassword()));
            TokenResponse tokenResponse = client.post()
                    .uri(this.smsProperties.getTokenUri())
                    .body(BodyInserters.fromFormData("grant_type", "client_credentials"))
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block();
            this.cacheService.saveValueWithExpiration(SMS_TOKEN_KEY, tokenResponse.getAccessToken(), TOKEN_TTL);
            return tokenResponse;

        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    protected void sendMessage(final String message, final String phoneNumber, String token) throws BusinessException {
        try {
            WebClient client = this.createSendClient(HeaderUtils.BEARER_HEADER + token);
            String response = client.post()
                    .uri(this.smsProperties.getSendUri())
                    .body(BodyInserters.fromValue(SendRequest.builder()
                            .outboundSMSMessageRequest(OutboundSMSMessageRequest.builder()
                                    .address(String.format(ADDRESS, phoneNumber))
                                    .senderAddress(SENDER)
                                    .outboundSMSTextMessage(OutboundSMSTextMessage.builder()
                                            .message(message)
                                            .build())
                                    .build())
                            .build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("Send sms successfully {}", response);
        } catch (Exception e) {
            log.error("Error sending SMS message", e);
        }
    }

    private WebClient createTokenClient(String token) {
        HttpClient httpClient = this.httpClientService.configureHttpClient();
        return WebClient
                .builder()
                .filter(ExchangeFilterFunction.ofResponseProcessor(RestClientUtils::renderApiErrorResponse))
                .baseUrl(this.smsProperties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.AUTHORIZATION, token)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .build();
    }

    private WebClient createSendClient(String token) {
        HttpClient httpClient = this.httpClientService.configureHttpClient();
        return WebClient
                .builder()
                .filter(ExchangeFilterFunction.ofResponseProcessor(RestClientUtils::renderApiErrorResponse))
                .baseUrl(this.smsProperties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.AUTHORIZATION, token)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private String getBasicAuthCreds(String username, String password) {
        final String auth = username + ":" + password;
        final byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.ISO_8859_1));
        return HeaderUtils.BASIC_HEADER + new String(encodedAuth);
    }
}
