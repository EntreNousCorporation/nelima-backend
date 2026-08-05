package com.ypyit.neoelima.common.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/**
 * Montant en francs CFA, tel qu'il s'écrit dans un message aux familles.
 *
 * <p>Le formatage passe par {@link Locale#ROOT} et non par la locale de la machine. Avec la locale
 * par défaut, {@code %,d} rend un séparateur qui dépend du système — une espace insécable sous une
 * locale française — et le remplacement de la virgule qui suit ne trouve alors rien à remplacer.
 * Le message partait avec un caractère invisible là où on croyait avoir mis une espace, et rien ne
 * le signalait : à l'écran les deux se ressemblent.
 *
 * <p>Le franc CFA n'a pas de subdivision : le montant est arrondi à l'unité.
 */
public final class XofFormat {

    private XofFormat() {
    }

    public static String format(BigDecimal amount) {
        if (Objects.isNull(amount)) {
            return "0 FCFA";
        }
        long units = amount.setScale(0, RoundingMode.HALF_UP).longValue();
        return String.format(Locale.ROOT, "%,d FCFA", units).replace(',', ' ');
    }
}
