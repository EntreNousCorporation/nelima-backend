package com.ypyit.neoelima.domain.authentication.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder(alphabetic = true)
public class Token {

    @JsonProperty("access_token")
    private String accessToken;
    @JsonProperty("expired_at")
    private long expiredAt;
    @Builder.Default
    @JsonProperty("token_type")
    private String tokenType = "Bearer";
    @JsonProperty("last_login")
    private String lastLogin;
}
