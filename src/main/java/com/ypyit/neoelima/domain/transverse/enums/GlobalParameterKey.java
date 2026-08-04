package com.ypyit.neoelima.domain.transverse.enums;

public enum GlobalParameterKey {

    RESET_USER_PWD_TOKEN_DELAY,

    RESET_USER_PWD_OTP_DELAY,

    /**
     * Taux de commission YPYit sur les paiements en ligne, en fraction ({@code 0.02} pour 2 %).
     *
     * <p>Stocké en base et non plus figé dans l'environnement : le modifier ne doit pas demander
     * un redéploiement. La variable `COMMISSION_RATE` en reste la valeur de repli.
     */
    COMMISSION_RATE
}
