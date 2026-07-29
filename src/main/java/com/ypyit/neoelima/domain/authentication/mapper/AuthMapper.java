package com.ypyit.neoelima.domain.authentication.mapper;

import com.ypyit.neoelima.domain.authentication.dto.TokenIntrospection;
import com.ypyit.neoelima.domain.utils.CustomClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public TokenIntrospection toIntrospection(Jws<Claims> claimsJws) {
        return TokenIntrospection
                .builder()
                .username((String) claimsJws.getBody().get(CustomClaims.USERNAME))
                .build();
    }
}
