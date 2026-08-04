-- Les activités extra-scolaires n'existaient pas. Une école qui facture le judo devait le faire
-- comme un frais ordinaire — donc à tous les élèves d'un niveau, qu'ils y participent ou non,
-- puisque le niveau est le seul ciblage qu'un frais connaisse.
create table activity (
    id               uuid primary key,
    created_at       timestamp(6) with time zone,
    updated_at       timestamp(6) with time zone,
    created_by       varchar(255),
    modified_by      varchar(255),
    name             varchar(128) not null,
    kind             varchar(32)  not null,
    place            varchar(128),
    day_of_week      varchar(16),
    start_time       time,
    end_time         time,
    period_label     varchar(64),
    -- Contraignante, contrairement à la capacité d'une classe : au-delà, l'inscription part en
    -- liste d'attente. Un cours de judo a le nombre de tapis qu'il a.
    capacity         integer      not null,
    status           varchar(32)  not null default 'DRAFT',
    -- Ouverte à tout l'établissement. Un indicateur plutôt que l'énumération des classes : sans
    -- lui, il faudrait rattacher chaque classe créée par la suite.
    open_to_all      boolean      not null default false,
    coach_id         uuid references staff (id),
    -- Le frais qui porte le tarif. Nul pour une activité gratuite.
    fee_id           uuid references fee (id),
    establishment_id uuid         not null references establishment (id),
    constraint uk_activity_establishment_name unique (establishment_id, name)
);

create index idx_activity_establishment on activity (establishment_id);

-- Classes autorisées à participer, quand l'activité n'est pas ouverte à tous.
create table activity_class (
    activity_id     uuid not null references activity (id) on delete cascade,
    school_class_id uuid not null references school_class (id) on delete cascade,
    primary key (activity_id, school_class_id)
);

-- Une ligne par élève et par activité : se réinscrire après une annulation reprend la ligne.
-- Empiler les demandes ferait compter deux fois le même élève dans les places occupées.
create table activity_enrollment (
    id             uuid primary key,
    created_at     timestamp(6) with time zone,
    updated_at     timestamp(6) with time zone,
    created_by     varchar(255),
    modified_by    varchar(255),
    status         varchar(32) not null,
    source         varchar(32) not null,
    -- Ordonne la liste d'attente : la première place libérée revient à celui qui a demandé le
    -- premier, et non au dernier que le secrétariat a sous les yeux.
    requested_at   timestamp(6) with time zone not null,
    activity_id    uuid        not null references activity (id) on delete cascade,
    student_id     uuid        not null references student (id) on delete cascade,
    -- Nulle tant qu'aucune place n'est obtenue : on ne facture pas une place qu'on n'a pas.
    student_fee_id uuid references student_fee (id),
    constraint uk_activity_enrollment_activity_student unique (activity_id, student_id)
);

create index idx_activity_enrollment_activity on activity_enrollment (activity_id, status);
create index idx_activity_enrollment_student on activity_enrollment (student_id);
