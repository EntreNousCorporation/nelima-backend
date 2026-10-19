-- Cantine : formules, abonnements, régimes, menus, pointage.

-- Régimes et allergies : au dossier de l'élève, lus par la cantine (fiche cuisine).
create table student_diet
(
    id          uuid primary key,
    created_at  timestamp(6) with time zone,
    updated_at  timestamp(6) with time zone,
    created_by  varchar(255),
    modified_by varchar(255),
    student_id  uuid        not null references student (id) on delete cascade,
    kind        varchar(32) not null,
    -- Précision libre, pour une allergie que la liste ne nomme pas.
    label       varchar(128),
    constraint uk_student_diet unique (student_id, kind)
);

-- Formules de l'année : 5 jours, 4 jours, ponctuel… chacune son tarif mensuel.
create table canteen_formula
(
    id               uuid primary key,
    created_at       timestamp(6) with time zone,
    updated_at       timestamp(6) with time zone,
    created_by       varchar(255),
    modified_by      varchar(255),
    establishment_id uuid           not null references establishment (id),
    academic_year_id uuid references academic_year (id),
    name             varchar(64)    not null,
    -- Jours servis, « MONDAY,TUESDAY,… » ; vide pour une formule ponctuelle.
    days             varchar(80),
    monthly_price    numeric(38, 2) not null,
    fee_id           uuid references fee (id),
    active           boolean        not null default true,
    constraint uk_canteen_formula unique (establishment_id, academic_year_id, name)
);

create table canteen_subscription
(
    id               uuid primary key,
    created_at       timestamp(6) with time zone,
    updated_at       timestamp(6) with time zone,
    created_by       varchar(255),
    modified_by      varchar(255),
    student_id       uuid not null references student (id) on delete cascade,
    formula_id       uuid not null references canteen_formula (id),
    academic_year_id uuid references academic_year (id),
    start_date       date not null,
    end_date         date,
    student_fee_id   uuid references student_fee (id)
);
create index idx_canteen_subscription_year on canteen_subscription (academic_year_id, student_id);

-- Menu du jour ; publié, il est visible des familles.
create table canteen_menu_day
(
    id                     uuid primary key,
    created_at             timestamp(6) with time zone,
    updated_at             timestamp(6) with time zone,
    created_by             varchar(255),
    modified_by            varchar(255),
    establishment_id       uuid    not null references establishment (id),
    day                    date    not null,
    starter                varchar(128),
    main_course            varchar(128),
    dessert                varchar(128),
    vegetarian_alternative varchar(128),
    -- Allergènes présents, « PEANUT,LACTOSE,… ».
    allergens              varchar(160),
    -- Jour férié, sortie scolaire : aucun repas n'est attendu.
    closed                 boolean not null default false,
    published              boolean not null default false,
    constraint uk_canteen_menu_day unique (establishment_id, day)
);

-- Pointage : seules les exceptions s'enregistrent (absent signalé, ou ponctuel servi).
create table canteen_attendance
(
    id          uuid primary key,
    created_at  timestamp(6) with time zone,
    updated_at  timestamp(6) with time zone,
    created_by  varchar(255),
    modified_by varchar(255),
    student_id  uuid        not null references student (id) on delete cascade,
    day         date        not null,
    status      varchar(16) not null,
    constraint uk_canteen_attendance unique (student_id, day)
);
