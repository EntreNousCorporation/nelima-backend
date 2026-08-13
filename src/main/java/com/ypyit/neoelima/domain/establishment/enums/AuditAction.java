package com.ypyit.neoelima.domain.establishment.enums;

/**
 * Ce que le journal consigne.
 *
 * <p>Six actions, et six seulement : l'argent qui entre, la dette qu'on redéfinit, les données
 * qu'on sort, les familles qu'on sollicite, et les accès qu'on ouvre ou qu'on ferme. Un journal qui
 * consigne tout ne consigne rien d'utile — le bruit noie ce qu'on y cherche.
 */
public enum AuditAction {

    /** Encaissement au guichet : de l'argent liquide est entré. */
    PAYMENT_COLLECTED,

    /** Échéancier d'un frais redéfini : ce que les familles doivent a changé. */
    FEE_SCHEDULE_DEFINED,

    /**
     * Un niveau a été retiré d'un frais, et les dettes des élèves de ce niveau avec lui.
     *
     * <p>Cet acte détruit des lignes d'échéancier. Il n'est permis que tant que rien n'a été
     * encaissé, mais il doit se relire : c'est ce qui explique qu'une famille ne doive plus rien.
     */
    FEE_LEVEL_DETACHED,

    /** Journal comptable exporté : des données nominatives sont sorties de la plateforme. */
    ACCOUNTING_EXPORTED,

    /** Campagne de relance envoyée : des familles ont été sollicitées, parfois à un coût. */
    REMINDER_CAMPAIGN_SENT,

    /** Accès au portail ouvert à un membre du personnel. */
    PORTAL_ACCESS_GRANTED,

    /** Accès au portail fermé. */
    PORTAL_ACCESS_REVOKED
}
