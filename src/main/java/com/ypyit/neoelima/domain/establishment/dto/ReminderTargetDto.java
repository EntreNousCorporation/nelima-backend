package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.domain.establishment.enums.ReminderTarget;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Ce qu'une cible représente, avant d'envoyer quoi que ce soit.
 *
 * <p>Un SMS se facture à l'envoi : une école doit savoir combien de familles elle s'apprête à
 * joindre, et pour quel montant en jeu, avant de cliquer. Découvrir l'ampleur après coup est
 * exactement ce qu'on veut éviter.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderTargetDto {

    private ReminderTarget target;
    private String label;

    /** Tranches concernées. */
    private long installmentCount;

    /** Familles distinctes : c'est ce nombre qui approche le coût, pas celui des tranches. */
    private long familyCount;

    /** Tuteurs joignables, tous canaux confondus. Un élève sans tuteur rattaché n'en a aucun. */
    private long recipientCount;

    private BigDecimal amountDue;
}
