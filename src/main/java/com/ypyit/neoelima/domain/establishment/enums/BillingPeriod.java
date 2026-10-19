package com.ypyit.neoelima.domain.establishment.enums;

/** Rythme de facturation d'un abonnement (cantine, transport) : au mois ou au trimestre. */
public enum BillingPeriod {
    MONTHLY,
    /** Une tranche par trimestre de l'école, au tarif mensuel multiplié par les mois couverts. */
    TERM
}
