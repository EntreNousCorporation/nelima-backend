package com.ypyit.neoelima.domain.feedback.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serial;

/**
 * Ce qu'un parent a à nous dire.
 *
 * <p>Enregistré <em>et</em> envoyé par courriel à YPYit. Le seul courriel suffirait à prévenir, mais
 * pas à relire : une suggestion perdue dans une boîte partagée n'a pas plus d'existence qu'un champ
 * qui ne mène nulle part, et le parent, lui, croit avoir été entendu.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "feedback")
public class FeedbackEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private UserEntity user;

    @Column(nullable = false, length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FeedbackStatus status;

    /**
     * Version de l'application au moment du dépôt.
     *
     * <p>C'est la première chose que le support demande, et la réclamer au parent après coup est le
     * meilleur moyen de ne jamais l'obtenir.
     */
    @Column(length = 32)
    private String appVersion;
}
