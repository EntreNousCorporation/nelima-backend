-- Le répertoire du personnel manquait : un enseignant n'existait dans la plateforme que sous forme
-- de nom libre recopié sur la classe dont il est titulaire. On ne pouvait ni le retrouver, ni
-- savoir où il intervient, ni pointer sa présence.
create table staff (
    id              uuid primary key,
    created_at      timestamp(6) with time zone,
    updated_at      timestamp(6) with time zone,
    created_by      varchar(255),
    modified_by     varchar(255),
    first_name      varchar(128) not null,
    last_name       varchar(128) not null,
    role            varchar(32)  not null,
    job_title       varchar(128),
    phone           varchar(64),
    email           varchar(128),
    contract_type   varchar(32),
    monthly_salary  numeric(38, 2),
    weekly_hours    integer,
    hired_at        date,
    active          boolean      not null default true,
    establishment_id uuid        not null references establishment (id),
    -- Le compte de connexion reste facultatif : c'est ce qui distingue le répertoire du personnel
    -- de la liste des comptes. La plupart des enseignants n'ont pas accès au portail.
    user_id         uuid unique references users (id)
);

create index idx_staff_establishment on staff (establishment_id);

-- Les classes où un membre intervient. Le titulariat, lui, se lit depuis la classe.
create table staff_class (
    staff_id        uuid not null references staff (id) on delete cascade,
    school_class_id uuid not null references school_class (id) on delete cascade,
    primary key (staff_id, school_class_id)
);

-- Une ligne par personne et par jour. Sans cette unicité, pointer deux fois la même personne le
-- même matin produirait deux vérités contradictoires dans le même tableau.
create table staff_attendance (
    id          uuid primary key,
    created_at  timestamp(6) with time zone,
    updated_at  timestamp(6) with time zone,
    created_by  varchar(255),
    modified_by varchar(255),
    day         date        not null,
    status      varchar(32) not null,
    note        varchar(255),
    staff_id    uuid        not null references staff (id) on delete cascade,
    constraint uk_staff_attendance_staff_day unique (staff_id, day)
);

create index idx_staff_attendance_day on staff_attendance (day);

-- Permissions héritées d'un autre projet, jamais rattachées à un rôle ici. Elles sont remplacées
-- par le catalogue réel au démarrage ; ces deux-là n'y figurent plus et resteraient orphelines.
delete from role_permissions where permission_id in (
    select id from permission where code in ('farm_owner', 'store_owner')
);
delete from permission where code in ('farm_owner', 'store_owner');
