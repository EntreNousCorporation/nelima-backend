package com.ypyit.neoelima.domain.establishment.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Groupes scolaires et leurs sites. */
public final class SchoolSiteDtos {

    private SchoolSiteDtos() {
    }

    @Getter
    @Builder
    public static class Site {
        private UUID id;
        private String name;
        private String city;
        /** Logo déposé par le site, s'il y en a un. */
        private String logo;
    }

    /** Le site affiché et ceux que l'appelant peut afficher. */
    @Getter
    @Builder
    public static class Sites {
        private String groupName;
        private boolean groupWide;
        private UUID homeId;
        private UUID currentId;
        private List<Site> sites;
    }

    @Getter
    @Builder
    public static class SiteFigures {
        private Site site;
        private long students;
        private long staff;
        private BigDecimal expected;
        private BigDecimal collected;
        private BigDecimal overdue;
        private int collectionRate;
    }

    @Getter
    @Builder
    public static class Overview {
        private String groupName;
        private LocalDate asOf;
        private List<SiteFigures> sites;
    }

    @Getter
    @Builder
    public static class Member {
        private UUID id;
        private String name;
        private String roleCode;
        private String siteName;
        private boolean groupWide;
    }

    @Getter
    @Builder
    public static class Group {
        private UUID id;
        private String name;
        private List<Site> sites;
        private List<Member> members;
    }
}
