package com.ypyit.neoelima.domain.writeoff.enums;

/** Où en est une demande de passage en perte. */
public enum WriteOffStatus {
    /** Proposée par la comptabilité, en attente de la direction. */
    PROPOSED,
    /** Validée : la tranche est passée en perte. */
    APPROVED,
    /** Refusée : la tranche reste due. */
    REJECTED
}
