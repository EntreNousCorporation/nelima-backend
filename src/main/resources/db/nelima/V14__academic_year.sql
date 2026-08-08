-- L'année scolaire n'existait nulle part. Une école ne pouvait pas déclarer ses bornes ni ses
-- trimestres, alors que c'est la maille à laquelle elle raisonne : les frais s'y rapportent, les
-- échéances s'y placent, et une direction lit son calendrier par période.
create table academic_year (
    id               uuid primary key,
    created_at       timestamp(6) with time zone,
    updated_at       timestamp(6) with time zone,
    created_by       varchar(255),
    modified_by      varchar(255),
    label            varchar(32) not null,
    start_date       date        not null,
    end_date         date        not null,
    -- Une seule active à la fois, garantie au service : deux années actives feraient répondre deux
    -- dates différentes à la question « où en est-on ? ».
    active           boolean     not null default false,
    establishment_id uuid        not null references establishment (id),
    constraint uk_academic_year_establishment_label unique (establishment_id, label)
);

create index idx_academic_year_establishment on academic_year (establishment_id);

-- Le nombre de périodes n'est pas fixé : imposer trois trimestres exclurait les écoles qui n'en
-- tiennent que deux.
create table academic_period (
    id               uuid primary key,
    created_at       timestamp(6) with time zone,
    updated_at       timestamp(6) with time zone,
    created_by       varchar(255),
    modified_by      varchar(255),
    label            varchar(64) not null,
    start_date       date        not null,
    end_date         date        not null,
    position         integer     not null,
    academic_year_id uuid        not null references academic_year (id) on delete cascade
);

create index idx_academic_period_year on academic_period (academic_year_id, position);
