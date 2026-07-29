package com.ypyit.neoelima.config.security;

import com.ypyit.neoelima.domain.authentication.service.AuthService;
import com.ypyit.neoelima.domain.user.entity.UserSessionEntity;
import com.ypyit.neoelima.domain.user.service.SessionService;
import com.ypyit.neoelima.domain.utils.HeaderUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final AuthService authService;
    
    private final SessionService sessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = HeaderUtils.extractToken(request);
        if (StringUtils.isNotBlank(token)) {
            String sessionId = authService.extractSessionId(token);
            Optional<UserSessionEntity> optionalWebSession = sessionService.getBySessionId(UUID.fromString(sessionId));
            if (optionalWebSession.isPresent() && Objects.isNull(optionalWebSession.get().getSessionEndTime())) {
                UsernamePasswordAuthenticationToken authentication = authService.getAuthentication(token);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
