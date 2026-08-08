package com.ypyit.neoelima.domain.establishment.enums;

/**
 * Nature d'une notification.
 *
 * <p>Peu de natures, et chacune appelle un geste. Une cloche qui annonce ce sur quoi on ne peut
 * rien se vide de son sens en une semaine : on cesse de la regarder, et le jour où elle porte
 * quelque chose d'important, personne ne la voit.
 *
 * <p>Deux publics se partagent l'énumération. {@link #INSTALLMENT_OVERDUE} est la seule nature
 * commune, et elle ne dit pas la même chose des deux côtés : l'école y lit une créance à relancer,
 * la famille une dette à régler. Le reste est propre à l'un ou à l'autre.
 */
public enum NotificationKind {

    /* ---------- Côté école ---------- */

    /** Une famille a inscrit son enfant à une activité depuis l'application. */
    ACTIVITY_REQUEST,

    /** Un encaissement est confirmé mais aucun reçu n'a été émis : pièce comptable manquante. */
    PAYMENT_TO_RECONCILE,

    /** Une échéance est arrivée à terme sans règlement. Lue des deux côtés. */
    INSTALLMENT_OVERDUE,

    /* ---------- Côté famille ---------- */

    /**
     * Une échéance approche.
     *
     * <p>Aux mêmes jours que le rappel automatique — J-7 puis J-1 — pour que la cloche et le
     * téléphone racontent la même chose.
     */
    INSTALLMENT_DUE_SOON,

    /** Un reçu a été émis pour un enfant du compte : le paiement est allé au bout. */
    PAYMENT_CONFIRMED,

    /** L'école a publié un événement visible des familles et qui concerne l'enfant. */
    SCHOOL_EVENT
}
