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
    COMMISSION_RATE,

    /**
     * Tarifs annuels des formules d'abonnement, en francs CFA.
     *
     * <p>En base plutôt que dans {@code SubscriptionPlan} pour la même raison que le taux de
     * commission : un prix se renégocie, et le changer ne doit pas demander un redéploiement. La
     * grille du business plan sert de valeur de repli tant qu'aucun prix n'a été enregistré.
     */
    SUBSCRIPTION_PRICE_DECOUVERTE,

    SUBSCRIPTION_PRICE_STANDARD,

    SUBSCRIPTION_PRICE_PRO,

    SUBSCRIPTION_PRICE_ENTERPRISE
}
