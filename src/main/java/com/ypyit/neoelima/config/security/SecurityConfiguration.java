package com.ypyit.neoelima.config.security;

import com.ypyit.neoelima.domain.user.service.impl.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collections;
import java.util.Objects;
import java.util.List;

import static com.ypyit.neoelima.domain.utils.SecurityUtils.AUTH_RESOURCES;
import static com.ypyit.neoelima.domain.utils.SecurityUtils.PAYMENT_ADMIN_RESOURCES;
import static com.ypyit.neoelima.domain.utils.SecurityUtils.PLATFORM_SETTINGS_WRITE_RESOURCES;
import static com.ypyit.neoelima.domain.utils.SecurityUtils.PAYMENT_WEBHOOK_RESOURCES;
import static com.ypyit.neoelima.domain.utils.SecurityUtils.GLOBAL_RESOURCES;
import static com.ypyit.neoelima.domain.utils.SecurityUtils.SWAGGER_RESOURCES;
import static com.ypyit.neoelima.domain.utils.SecurityUtils.USER_GET_RESOURCES;
import static com.ypyit.neoelima.domain.utils.SecurityUtils.USER_POST_RESOURCES;
import static com.ypyit.neoelima.domain.utils.SecurityUtils.USER_PUT_RESOURCES;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private static final String MATCH_ALL = "/**";

    private final JwtAuthFilter jwtAuthFilter;

    private final UserDetailsServiceImpl userService;

    private final CurrentUserProvider currentUserProvider;

    /**
     * Réserve l'accès à l'équipe YPYit.
     *
     * <p>On s'appuie sur le type d'utilisateur et non sur une autorité portée par le jeton : les
     * rôles seedés n'ont aujourd'hui aucune permission associée, un {@code hasAuthority} ne
     * protégerait donc rien du tout.
     */
    private AuthorizationManager<RequestAuthorizationContext> platformAdminOnly() {
        return (authentication, context) -> {
            Authentication current = authentication.get();
            if (Objects.isNull(current) || !current.isAuthenticated()) {
                return new AuthorizationDecision(false);
            }
            try {
                return new AuthorizationDecision(this.currentUserProvider.isPlatformAdmin());
            } catch (RuntimeException e) {
                return new AuthorizationDecision(false);
            }
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain usernamePassword(HttpSecurity http) throws Exception {

        AuthenticationManagerBuilder authenticationManagerBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder.userDetailsService(this.userService);
        AuthenticationManager authenticationManager = authenticationManagerBuilder.build();

        http.securityMatcher(new AntPathRequestMatcher(MATCH_ALL))
                .cors(customizer -> customizer
                        .configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> {
                    authorize
                            .requestMatchers(HttpMethod.POST, USER_POST_RESOURCES).permitAll()
                            .requestMatchers(HttpMethod.PUT, USER_PUT_RESOURCES).permitAll()
                            .requestMatchers(HttpMethod.GET, USER_GET_RESOURCES).permitAll()
                            .requestMatchers(SWAGGER_RESOURCES).permitAll()
                            .requestMatchers(AUTH_RESOURCES).permitAll()
                            // L'agrégateur appelle sans jeton Nelima ; c'est la signature HMAC de
                            // la charge utile, vérifiée par PaySwitch, qui fait foi.
                            .requestMatchers(PAYMENT_WEBHOOK_RESOURCES).permitAll()
                            // Ces routes exposent les credentials de l'agrégateur et permettent de
                            // basculer le provider actif. Le starter ne les protège pas.
                            .requestMatchers(PAYMENT_ADMIN_RESOURCES).access(platformAdminOnly())
                            .requestMatchers(HttpMethod.PUT, PLATFORM_SETTINGS_WRITE_RESOURCES).access(platformAdminOnly())
                            .requestMatchers(HttpMethod.POST, PLATFORM_SETTINGS_WRITE_RESOURCES).access(platformAdminOnly())
                            .requestMatchers(HttpMethod.DELETE, PLATFORM_SETTINGS_WRITE_RESOURCES).access(platformAdminOnly())
                            .requestMatchers(HttpMethod.GET, GLOBAL_RESOURCES).permitAll();
                    authorize
                            .anyRequest()
                            .authenticated();
                    http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
                })
                .sessionManagement((sessionManagement) ->
                        sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS
                        ))
                .authenticationManager(authenticationManager)
                .exceptionHandling(exceptionHandling ->
                        exceptionHandling
                                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(Collections.singletonList("*"));
        cors.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.HEAD.name(),
                HttpMethod.PUT.name()));
        cors.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION.toLowerCase(),
                HttpHeaders.CONTENT_TYPE.toLowerCase()));
        cors.setExposedHeaders(Collections.singletonList(HttpHeaders.LOCATION));
        cors.setAllowCredentials(true);
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
