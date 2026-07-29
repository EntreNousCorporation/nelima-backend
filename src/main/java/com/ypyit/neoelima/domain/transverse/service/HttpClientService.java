package com.ypyit.neoelima.domain.transverse.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Service
public class HttpClientService {

    @Value("${neo-elima.web-client-timeout}")
    private long clientTimeout;


    public HttpClient configureHttpClient() {
        return HttpClient.create()
                .wiretap(true)
                .responseTimeout(Duration.ofMinutes(clientTimeout));
    }
}
