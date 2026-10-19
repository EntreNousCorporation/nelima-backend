package com.ypyit.neoelima.domain.transport.entity;

import com.ypyit.neoelima.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.Hibernate;

import java.io.Serial;
import java.time.LocalTime;
import java.util.Objects;

/** Arrêt d'une ligne, dans l'ordre du ramassage ; ses coordonnées le placent sur la carte. */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "transport_stop")
public class TransportStopEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "line_id", referencedColumnName = "id")
    private TransportLineEntity line;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false, length = 96)
    private String name;

    private LocalTime pickupTime;

    private Double latitude;

    private Double longitude;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        TransportStopEntity that = (TransportStopEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
