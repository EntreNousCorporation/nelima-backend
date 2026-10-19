package com.ypyit.neoelima.domain.transport.entity;

import com.ypyit.neoelima.domain.transport.enums.LineStatus;
import com.ypyit.neoelima.common.entity.BaseEntity;
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
import org.hibernate.Hibernate;

import java.io.Serial;
import java.time.LocalDate;
import java.util.Objects;

/** Où en est la ligne ce jour-là. */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "transport_line_status")
public class TransportLineStatusEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JoinColumn(name = "line_id", referencedColumnName = "id")
    private TransportLineEntity line;

    @Column(nullable = false)
    private LocalDate day;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LineStatus status;

    private Integer delayMinutes;

    @Column(length = 255)
    private String note;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        TransportLineStatusEntity that = (TransportLineStatusEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
