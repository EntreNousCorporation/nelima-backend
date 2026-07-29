package com.ypyit.neoelima.domain.authentication.service;

import com.ypyit.neoelima.config.properties.SecurityProperties;
import com.ypyit.neoelima.domain.authentication.dto.Token;
import com.ypyit.neoelima.domain.authentication.dto.TokenIntrospection;
import com.ypyit.neoelima.domain.authentication.mapper.AuthMapper;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.form.ResetPwdRequest;
import com.ypyit.neoelima.domain.user.service.SessionService;
import com.ypyit.neoelima.domain.user.service.impl.UserDetailsServiceImpl;
import com.ypyit.neoelima.domain.utils.CustomClaims;
import com.ypyit.neoelima.domain.utils.FunctionalUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SessionService sessionService;
    private final UserDetailsServiceImpl userDetailsService;
    private final AuthMapper authMapper;
    private final SecurityProperties securityProperties;

    public String extractUsername(String token) {
        return extractClaim(token, (Claims claims) -> claims.get(CustomClaims.USERNAME, String.class));
    }

    public String extractSessionId(String token) {
        return extractClaim(token, (Claims claims) -> claims.get(CustomClaims.SESSION_ID, String.class));
    }

    public String extractIssuer(String token) {
        return extractClaim(token, Claims::getIssuer);
    }

    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser().setSigningKey(this.securityProperties.getSecret()).parseClaimsJws(token).getBody();
    }

    public Boolean tokenHasNotExpired(String token) {
        return extractExpiration(token).after(new Date());
    }

    public Token generateToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        UserEntity user = this.userDetailsService.loadUserByUsername(username);

        UUID sessionId = sessionService.startSession(user.getId());

        claims.put(CustomClaims.USERNAME, username);
        claims.put(CustomClaims.LAST_LOGIN,
                FunctionalUtils.getOrEmpty(() -> user.getLastLogin().toString()).orElse(""));
        claims.put(CustomClaims.SESSION_ID, sessionId.toString());
        Optional.ofNullable(user.getAuthorities())
                .ifPresent(grantedAuthorities -> {
                    List<String> permissions = user.getAuthorities()
                            .stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toList());
                    claims.put(CustomClaims.PERMISSIONS, permissions);
                });

        long startDate = System.currentTimeMillis();
        long tokenDuration = Long.sum(startDate, this.securityProperties.getExpirationDate());

        String tokenStringValue = Jwts
                .builder()
                .setClaims(claims)
                .setSubject(String.valueOf(user.getId()))
                .setIssuedAt(new Date(startDate))
                .setExpiration(new Date(tokenDuration))
                .setIssuer(this.securityProperties.getIssuer())
                .signWith(SignatureAlgorithm.HS256, this.securityProperties.getSecret()).compact();


        return Token.builder()
                .accessToken(tokenStringValue)
                .expiredAt(tokenDuration)
                .lastLogin(FunctionalUtils.getOrNull(() -> user.getLastLogin().toString()))
                .build();
    }

    private boolean isIssuedByMe(String token) {
        String issuer = extractIssuer(token);
        return this.securityProperties.getIssuer().equals(issuer);
    }

    public Boolean validateToken(String token) {

        try {
            return (tokenHasNotExpired(token) && isIssuedByMe(token));
        } catch (ExpiredJwtException ex) {
            log.warn("token has expired");
            return false;
        } catch (SignatureException ex) {
            log.warn("signature is not valid");
            return false;
        } catch (Exception ex) {
            log.warn("token not valid");
            return false;
        }
    }


    public UsernamePasswordAuthenticationToken getAuthentication(String token) {
        String username = this.extractUsername(token);
        if (Objects.isNull(username)) {
            throw new IllegalArgumentException("cannot extract username");
        }
        UserEntity userDetails = this.userDetailsService.loadUserByUsername(username);
        if (Objects.nonNull(userDetails) && this.validateToken(token)) {
            return new UsernamePasswordAuthenticationToken(username, token, userDetails.getAuthorities());
        }
        throw new IllegalArgumentException("cannot load user with username " + username);
    }

    public String generateResetPasswordToken(ResetPwdRequest resetPwdRequest) {
        Map<String, Object> claims = new HashMap<>();
        Assert.notNull(resetPwdRequest.getUser(), "User may not be null");
        claims.put(CustomClaims.USERNAME, resetPwdRequest.getUser().getUsername());

        long startDate = System.currentTimeMillis();
        long tokenDuration = Long.sum(startDate, resetPwdRequest.getNumberOfMilliSeconds());

        return Jwts
                .builder()
                .setClaims(claims)
                .setSubject(String.valueOf(resetPwdRequest.getUser().getId()))
                .setIssuedAt(new Date(startDate))
                .setExpiration(new Date(tokenDuration))
                .setIssuer(this.securityProperties.getIssuer())
                .signWith(SignatureAlgorithm.HS256, this.securityProperties.getSecret()).compact();

    }

    public TokenIntrospection parseJwt(String token) {
        return this.authMapper
                .toIntrospection(Jwts.parser()
                        .setSigningKey(this.securityProperties.getSecret())
                        .parseClaimsJws(token));
    }

    public void invalidateToken(String token) {
        try {
            Jws<Claims> jws = Jwts.parser().setSigningKey(this.securityProperties.getSecret()).parseClaimsJws(token);
            jws.getBody().setExpiration(new Date()).getSubject();
        } catch (Exception e) {
            log.error("Error while invalidate jwt {}", ExceptionUtils.getMessage(e));
        }
    }

}
