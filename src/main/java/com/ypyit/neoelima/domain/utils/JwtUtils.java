package com.ypyit.neoelima.domain.utils;

import com.ypyit.neoelima.domain.authentication.dto.Token;
import com.ypyit.neoelima.domain.authentication.dto.TokenIntrospection;
import com.ypyit.neoelima.domain.authentication.mapper.AuthMapper;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.service.SessionService;
import com.ypyit.neoelima.domain.user.service.impl.UserDetailsServiceImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

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
public class JwtUtils {
    @Value("${security.jwt.secret}")
    private String secret;

    @Value("${security.jwt.expiration-date}")
    private long numberOfMilliSeconds;

    @Value("${security.jwt.issuer}")
    private String tokenIssuer;

    private final SessionService sessionService;

    private final UserDetailsServiceImpl userService;

    private final AuthMapper jwtMapper;

    public String extractUsername(String token) {

        return extractClaim(token, (Claims claims) -> claims.get(CustomClaims.USERNAME, String.class));
    }

    public Long extractSessionId(String token) {
        return extractClaim(token, (Claims claims) -> claims.get(CustomClaims.SESSION_ID, Long.class));
    }

    public String extractIssuer(String token) {
        return extractClaim(token, Claims::getIssuer);
    }

    public Long extractUserId(String token) {
        return Long.valueOf(extractClaim(token, Claims::getSubject));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
    }

    public Boolean tokenHasNotExpired(String token) {
        return extractExpiration(token).after(new Date());
    }

    public Token generateToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        UserEntity user = userService.loadUserByUsername(username);

        UUID sessionId = sessionService.startSession(user.getId());

        claims.put(CustomClaims.USERNAME, username);
        claims.put(CustomClaims.SESSION_ID, sessionId);
        claims.put(CustomClaims.FIRST_NAME, user.getFirstName());
        claims.put(CustomClaims.LASTNAME, user.getLastName());

        Optional.ofNullable(user.getAuthorities())
                .ifPresent(grantedAuthorities -> {
                    List<String> permissions = user.getAuthorities()
                            .stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toList());
                    claims.put(CustomClaims.PERMISSIONS, permissions);
                });
        Optional.ofNullable(user.getRole())
                .ifPresent(role -> claims.put(CustomClaims.PROFILE, role.getCode()));
        long startDate = System.currentTimeMillis();
        long tokenDuration = Long.sum(startDate, numberOfMilliSeconds);

        String tokenStringValue = Jwts
                .builder()
                .setClaims(claims)
                .setSubject(user.getId().toString())
                .setIssuedAt(new Date(startDate))
                .setExpiration(new Date(tokenDuration))
                .setIssuer(tokenIssuer)
                .signWith(SignatureAlgorithm.HS256, secret).compact();


        return Token.builder()
                .accessToken(tokenStringValue)
                .expiredAt(tokenDuration)
                .build();
    }

    private boolean isIssuedByMe(String token) {
        String issuer = extractIssuer(token);
        return tokenIssuer.equals(issuer);
    }

    /**
     * signature is not checked here because it's already done when a claim is about extracting
     *
     * @param token
     * @return true if token is valid
     */
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
        UserEntity userDetails = userService.loadUserByUsername(username);
        if (Objects.nonNull(userDetails) && this.validateToken(token)) {
            return new UsernamePasswordAuthenticationToken(username, userDetails.getPassword(), userDetails.getAuthorities());
        }
        throw new IllegalArgumentException("cannot load user with username " + username);
    }

    public TokenIntrospection parseJwt(String token) {

        return this.jwtMapper
                .toIntrospection(Jwts.parser()
                        .setSigningKey(this.secret)
                        .parseClaimsJws(token));
    }

}
