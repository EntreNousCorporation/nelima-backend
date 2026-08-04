-- Le calendrier scolaire n'existait pas. Une direction tenait de tête ses conseils de classe, ses
-- réunions et ses examens, alors que les échéances d'argent, elles, étaient déjà dans la base.
--
-- Seule la vie scolaire est stockée ici : les échéances financières sont déduites des tranches
-- dues. Les écrire aussi dans cette table créerait une seconde vérité à côté de la dette réelle,
-- et rien ne garantirait qu'elles restent d'accord.
create table school_event (
    id                  uuid primary key,
    created_at          timestamp(6) with time zone,
    updated_at          timestamp(6) with time zone,
    created_by          varchar(255),
    modified_by         varchar(255),
    title               varchar(160) not null,
    kind                varchar(32)  not null,
    event_date          date         not null,
    all_day             boolean      not null default false,
    start_time          time,
    end_time            time,
    details             varchar(255),
    -- Concerne tout l'établissement, y compris les classes ouvertes par la suite.
    whole_school        boolean      not null default false,
    -- Paraît dans l'application des familles. N'envoie rien : rendre visible n'est pas interrompre.
    visible_to_families boolean      not null default false,
    -- Dernier envoi effectif, pour distinguer « personne n'a été prévenu » de « déjà prévenu ».
    last_notified_at    timestamp(6) with time zone,
    establishment_id    uuid         not null references establishment (id)
);

-- L'écran interroge toujours une fenêtre de dates sur un établissement : c'est le seul accès.
create index idx_school_event_establishment_date on school_event (establishment_id, event_date);

create table school_event_class (
    school_event_id uuid not null references school_event (id) on delete cascade,
    school_class_id uuid not null references school_class (id) on delete cascade,
    primary key (school_event_id, school_class_id)
);
