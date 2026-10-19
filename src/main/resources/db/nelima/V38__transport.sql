-- Transport scolaire : chauffeurs, lignes et arrêts, abonnements, suivi du jour.

create table transport_driver
(
    id               uuid primary key,
    created_at       timestamp(6) with time zone,
    updated_at       timestamp(6) with time zone,
    created_by       varchar(255),
    modified_by      varchar(255),
    establishment_id uuid         not null references establishment (id),
    first_name       varchar(64)  not null,
    last_name        varchar(64)  not null,
    phone            varchar(32),
    -- D (transport de personnes), C (poids lourd), B (minibus).
    license_category varchar(2),
    license_expiry   date,
    -- Pièces fournies, « LICENSE,ID,RECORD,MEDICAL ».
    documents        varchar(64),
    active           boolean      not null default true
);

-- Une ligne est celle d'une année : itinéraire, car, chauffeur et tarif changent à la rentrée.
create table transport_line
(
    id                 uuid primary key,
    created_at         timestamp(6) with time zone,
    updated_at         timestamp(6) with time zone,
    created_by         varchar(255),
    modified_by        varchar(255),
    establishment_id   uuid           not null references establishment (id),
    academic_year_id   uuid references academic_year (id),
    name               varchar(64)    not null,
    vehicle_plate      varchar(16),
    capacity           integer        not null,
    driver_id          uuid references transport_driver (id) on delete set null,
    morning_departure  time,
    evening_departure  time,
    -- Jours de circulation, « MONDAY,TUESDAY,… ».
    days               varchar(80)    not null default 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY',
    attendant          varchar(96),
    start_date         date,
    monthly_price      numeric(38, 2) not null,
    fee_id             uuid references fee (id),
    active             boolean        not null default true,
    constraint uk_transport_line unique (establishment_id, academic_year_id, name)
);

create table transport_stop
(
    id          uuid primary key,
    created_at  timestamp(6) with time zone,
    updated_at  timestamp(6) with time zone,
    created_by  varchar(255),
    modified_by varchar(255),
    line_id     uuid        not null references transport_line (id) on delete cascade,
    position    integer     not null,
    name        varchar(96) not null,
    pickup_time time,
    latitude    double precision,
    longitude   double precision
);
create index idx_transport_stop_line on transport_stop (line_id, position);

create table transport_subscription
(
    id               uuid primary key,
    created_at       timestamp(6) with time zone,
    updated_at       timestamp(6) with time zone,
    created_by       varchar(255),
    modified_by      varchar(255),
    student_id       uuid not null references student (id) on delete cascade,
    line_id          uuid not null references transport_line (id),
    stop_id          uuid references transport_stop (id) on delete set null,
    academic_year_id uuid references academic_year (id),
    start_date       date not null,
    end_date         date,
    student_fee_id   uuid references student_fee (id)
);
create index idx_transport_subscription_line on transport_subscription (line_id);

-- Où en est la ligne aujourd'hui, saisi par l'école : un statut par ligne et par jour.
create table transport_line_status
(
    id            uuid primary key,
    created_at    timestamp(6) with time zone,
    updated_at    timestamp(6) with time zone,
    created_by    varchar(255),
    modified_by   varchar(255),
    line_id       uuid        not null references transport_line (id) on delete cascade,
    day           date        not null,
    status        varchar(16) not null,
    delay_minutes integer,
    note          varchar(255),
    constraint uk_transport_line_status unique (line_id, day)
);
