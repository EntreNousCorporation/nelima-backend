package com.ypyit.neoelima.config.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.Objects;

public class LangInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        LocaleResolver localeResolver = RequestContextUtils.getLocaleResolver(request);
        if (Objects.nonNull(localeResolver)) {
            CurrentLocale.setValue(localeResolver.resolveLocale(request));
        }
        return true;
    }
}
