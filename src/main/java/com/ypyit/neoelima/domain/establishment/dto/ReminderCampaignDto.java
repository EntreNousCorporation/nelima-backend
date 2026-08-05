package com.ypyit.neoelima.domain.establishment.dto;

import com.ypyit.neoelima.domain.establishment.enums.ReminderChannel;
import com.ypyit.neoelima.domain.establishment.enums.ReminderTarget;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Une campagne et ce qu'elle a produit.
 *
 * <p>Les paiements déclenchés ne sont pas stockés : ils se déduisent des rappels émis et de l'état
 * des tranches visées. Les figer obligerait à les tenir à jour, et le chiffre divergerait dès
 * qu'une famille règle après coup.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderCampaignDto {

    private String id;
    private String name;
    private ReminderTarget target;
    private String targetLabel;

    @Builder.Default
    private List<ReminderChannel> channels = new ArrayList<>();

    private String messageTemplate;
    private Instant sentAt;

    /** Rappels effectivement partis. */
    private int sentCount;

    /** Destinataires écartés parce que déjà relancés le jour même. */
    private int skippedCount;

    /** Tranches visées qui ont été réglées depuis l'envoi. */
    private long paidCount;

    private BigDecimal recoveredAmount;
}
