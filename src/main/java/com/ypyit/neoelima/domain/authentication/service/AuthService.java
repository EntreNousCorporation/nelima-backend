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
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

    /** HMAC-SHA256 impose une clé d'au moins 256 bits, soit 32 octets. */
    private static final int MIN_SECRET_BYTES = 32;

    private final SessionService sessionService;
    private final UserDetailsServiceImpl userDetailsService;
    private final AuthMapper authMapper;
    private final SecurityProperties securityProperties;

    private SecretKey signingKey;

    /**
     * La clé de signature, ou un refus de démarrer.
     *
     * <p>Le repli d'{@code application.yml} était une valeur <strong>fonctionnelle</strong> de
     * 33 caractères, publiée dans ce dépôt : la seule assertion portant sur la longueur, un
     * environnement déployé sans {@code JWT_SECRET} démarrait normalement avec un secret que
     * quiconque lit le code peut reproduire — et forger un jeton pour n'importe quel compte.
     *
     * <p>C'est plus grave qu'une configuration manquante, parce que c'est <em>silencieux</em> : une
     * configuration manquante fait tomber le démarrage, et on la corrige dans la minute.
     *
     * <p>Le repli est donc vide, et le vide refusé ici. La production porte la variable depuis
     * toujours (vérifié) ; les tests fournissent la leur ; un poste de développement doit la poser,
     * ce qui est exactement l'intention.
     */
    @PostConstruct
    void initSigningKey() {
        String secret = this.securityProperties.getSecret();
        Assert.hasText(secret,
                "security.jwt.secret est obligatoire : renseignez la variable d'environnement JWT_SECRET");
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        Assert.isTrue(secretBytes.length >= MIN_SECRET_BYTES,
                "security.jwt.secret doit faire au moins " + MIN_SECRET_BYTES + " caractères");
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

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
        return Jwts.parser()
                .verifyWith(this.signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
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
                .claims(claims)
                .subject(String.valueOf(user.getId()))
                .issuedAt(new Date(startDate))
                .expiration(new Date(tokenDuration))
                .issuer(this.securityProperties.getIssuer())
                .signWith(this.signingKey)
                .compact();

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
        long tokenDuration = Long.sum(startDate,
                Duration.ofMinutes(resetPwdRequest.getValidityInMinutes()).toMillis());

        return Jwts
                .builder()
                .claims(claims)
                .subject(String.valueOf(resetPwdRequest.getUser().getId()))
                .issuedAt(new Date(startDate))
                .expiration(new Date(tokenDuration))
                .issuer(this.securityProperties.getIssuer())
                .signWith(this.signingKey)
                .compact();

    }

    public TokenIntrospection parseJwt(String token) {
        return this.authMapper
                .toIntrospection(Jwts.parser()
                        .verifyWith(this.signingKey)
                        .build()
                        .parseSignedClaims(token));
    }

}
