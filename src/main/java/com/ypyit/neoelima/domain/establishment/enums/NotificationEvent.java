package com.ypyit.neoelima.domain.establishment.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Événements pour lesquels l'école règle ce qui part aux familles.
 *
 * <p>Trois événements, et ce sont ceux qui existent réellement dans le produit. L'absence et le
 * bulletin relèvent de la vie scolaire, qui n'est pas construite.
 *
 * <p>Chaque événement porte <strong>les canaux qui le concernent</strong>, et non les trois par
 * principe : proposer un rappel d'échéance par courriel donnerait une case qui n'envoie rien, ce
 * qui fait douter de tout le reste de l'écran.
 */
public enum NotificationEvent {

    /**
     * Un encaissement vient d'être enregistré.
     *
     * <p>Le courriel n'est pas débrayable : il porte le reçu, pièce comptable, et le push ne fait
     * que l'annoncer. Le SMS n'est pas proposé — un reçu ne tient pas dans un SMS.
     */
    RECEIPT_ISSUED(EnumSet.of(NotificationChannel.PUSH, NotificationChannel.EMAIL),
            EnumSet.of(NotificationChannel.EMAIL)),

    /** Une tranche arrive à échéance : le rappel programmé, et les campagnes « échéance proche ». */
    INSTALLMENT_DUE_SOON(EnumSet.of(NotificationChannel.PUSH, NotificationChannel.SMS),
            EnumSet.noneOf(NotificationChannel.class)),

    /** Une tranche est échue et impayée : les campagnes de relance. */
    INSTALLMENT_OVERDUE(EnumSet.of(NotificationChannel.PUSH, NotificationChannel.SMS),
            EnumSet.noneOf(NotificationChannel.class));

    private final Set<NotificationChannel> supported;
    private final Set<NotificationChannel> locked;

    NotificationEvent(Set<NotificationChannel> supported, Set<NotificationChannel> locked) {
        this.supported = supported;
        this.locked = locked;
    }

    /** Canaux réellement raccordés à cet événement. */
    public Set<NotificationChannel> supportedChannels() {
        return this.supported;
    }

    /** Canaux que l'école ne peut pas couper, et qui sont affichés cochés avec leur raison. */
    public Set<NotificationChannel> lockedChannels() {
        return this.locked;
    }

    public boolean supports(NotificationChannel channel) {
        return this.supported.contains(channel);
    }

    public boolean isLocked(NotificationChannel channel) {
        return this.locked.contains(channel);
    }

    /**
     * Valeur par défaut <strong>pour l'école</strong>, faute de réglage enregistré.
     *
     * <p>Le SMS est éteint par défaut, et c'est délibéré : il se facture. Un canal payant qu'on
     * active sans le savoir se découvre sur la facture.
     */
    public boolean defaultFor(NotificationChannel channel) {
        return this.supports(channel) && !NotificationChannel.SMS.equals(channel);
    }

    /**
     * Valeur par défaut <strong>pour un parent</strong> : tout ce que son école lui envoie.
     *
     * <p>L'asymétrie avec {@link #defaultFor} n'est pas un oubli. Les deux réglages ne répondent pas
     * à la même question : l'école décide ce qu'elle <em>émet</em> et le paie, le parent décide ce
     * qu'il veut bien <em>recevoir</em>. Le premier est un engagement, le second un retrait.
     *
     * <p>Reprendre ici le défaut de l'école éteindrait le SMS de tous les parents à la fois : une
     * école qui a délibérément ouvert le canal — et le paie — n'atteindrait plus personne tant que
     * chaque famille n'aurait pas basculé un interrupteur qu'elle n'a jamais vu. Le silence d'un
     * parent vaut donc acceptation ; seul un refus explicite coupe.
     */
    public boolean defaultForParent(NotificationChannel channel) {
        return this.supports(channel);
    }
}
