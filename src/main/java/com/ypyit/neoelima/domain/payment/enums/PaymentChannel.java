package com.ypyit.neoelima.domain.payment.enums;

/**
 * Canal par lequel l'argent est entré.
 *
 * <p>Les encaissements hors ligne ne passent pas par PaySwitch mais doivent apparaître dans le
 * journal de l'école au même titre que les paiements en ligne : c'est le rapprochement de caisse.
 */
public enum PaymentChannel {
    ONLINE,
    CASH,
    CHECK,
    BANK_TRANSFER
}
