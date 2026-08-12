package com.ypyit.neoelima.domain.user.form;

import com.ypyit.neoelima.domain.user.entity.UserEntity;
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
public class ResetPwdRequest {

    private UserEntity user;

    /**
     * Durée de validité du jeton, <strong>en minutes</strong>.
     *
     * <p>Ce champ s'appelait {@code numberOfMilliSeconds} et recevait pourtant le paramètre
     * {@code RESET_USER_PWD_TOKEN_DELAY} — 60, exprimé en minutes, la même valeur servant
     * d'expiration à la copie Redis du jeton. Le JWT était donc daté à 60 <em>millisecondes</em> :
     * expiré avant d'atteindre la boîte du destinataire. Sans conséquence tant que
     * {@code resetPassword} se contente de comparer le jeton reçu à celui gardé en cache, mais le
     * jour où quelqu'un validera le JWT, le parcours cassera sans raison apparente.
     */
    private long validityInMinutes;
}
