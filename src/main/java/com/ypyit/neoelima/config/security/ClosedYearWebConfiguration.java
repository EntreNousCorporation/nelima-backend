package com.ypyit.neoelima.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Branche la lecture seule des années clôturées sur toutes les routes. */
@Configuration
@RequiredArgsConstructor
public class ClosedYearWebConfiguration implements WebMvcConfigurer {

    private final ClosedYearWriteInterceptor closedYearWriteInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this.closedYearWriteInterceptor);
    }
}
