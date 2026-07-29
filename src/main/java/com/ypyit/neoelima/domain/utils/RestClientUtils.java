package com.ypyit.neoelima.domain.utils;

import com.ypyit.neoelima.common.exception.BusinessException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

@Slf4j
@UtilityClass
public class RestClientUtils {

    public Mono<ClientResponse> renderApiErrorResponse(ClientResponse clientResponse) {
        if (clientResponse.statusCode().isError()) {
            return clientResponse.bodyToMono(String.class)
                    .defaultIfEmpty("No body in API response")
                    .flatMap(error -> {
                        log.error("Error while calling API {}", error);
                        return Mono.error(new BusinessException(error));
                    });
        }
        return Mono.just(clientResponse);
    }
}