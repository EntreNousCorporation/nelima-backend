-- Années scolaires de la plateforme, années d'école, et données rattachées à l'année.
--
-- Jusqu'ici chaque école déclarait ses années. Le calendrier scolaire est national : la plateforme
-- définit désormais les années (libellé, dates, année en cours) pour toutes les écoles. Chaque
-- école garde sa vision de l'année — ses trimestres, et bientôt son état ouvert ou clôturé — dans
-- une « année d'école » (school_year). Les données de l'école (frais, classes ouvertes, calendrier,
-- activités, inscriptions) se rattachent à l'année.

-- ---------------------------------------------------------------------------------------------
-- 1. Année d'école : une par école et par année.
-- ---------------------------------------------------------------------------------------------
create table school_year
(
    id               uuid primary key,
    created_at       timestamp(6) with time zone,
    updated_at       timestamp(6) with time zone,
    created_by       varchar(255),
    modified_by      varchar(255),
    establishment_id uuid        not null references establishment (id),
    academic_year_id uuid        not null references academic_year (id),
    status           varchar(16) not null default 'OPEN',
    closed_at        timestamp(6) with time zone,
    closed_by        varchar(255),
    legacy_year_id   uuid,
    constraint uk_school_year unique (establishment_id, academic_year_id)
);

insert into school_year (id, created_at, updated_at, created_by, modified_by,
                         establishment_id, academic_year_id, legacy_year_id)
select gen_random_uuid(), now(), now(), 'migration-V35', 'migration-V35', y.establishment_id, y.id, y.id
  from academic_year y
 where y.establishment_id is not null;

-- ---------------------------------------------------------------------------------------------
-- 2. Les trimestres appartiennent à l'année d'école : chaque école garde les siens.
-- ---------------------------------------------------------------------------------------------
alter table academic_period add column school_year_id uuid references school_year (id) on delete cascade;
update academic_period p
   set school_year_id = sy.id
  from school_year sy
 where sy.legacy_year_id = p.academic_year_id;

-- ---------------------------------------------------------------------------------------------
-- 3. Une année de plateforme par libellé : la plus ancienne sert de référence, ses dates
--    deviennent l'enveloppe de celles des écoles.
-- ---------------------------------------------------------------------------------------------
create temporary table year_canon as
select label,
       (array_agg(id order by created_at nulls last, id))[1] as canon_id,
       min(start_date)                                       as start_date,
       max(end_date)                                         as end_date,
       bool_or(active)                                       as active
  from academic_year
 group by label;

update school_year sy
   set academic_year_id = c.canon_id
  from academic_year y
  join year_canon c on c.label = y.label
 where sy.academic_year_id = y.id;

update student_enrollment se
   set academic_year_id = c.canon_id
  from academic_year y
  join year_canon c on c.label = y.label
 where se.academic_year_id = y.id;

alter table academic_period drop column academic_year_id;
alter table academic_period alter column school_year_id set not null;
create index idx_academic_period_school_year on academic_period (school_year_id, position);

update academic_year y
   set start_date = c.start_date,
       end_date   = c.end_date,
       active     = c.active
  from year_canon c
 where y.id = c.canon_id;

delete from academic_year y
 where not exists (select 1 from year_canon c where c.canon_id = y.id);

alter table academic_year drop column establishment_id;
alter table academic_year add constraint uk_academic_year_label unique (label);
-- Une seule année en cours pour toute la plateforme.
create unique index ux_academic_year_active on academic_year ((true)) where active;

alter table school_year drop column legacy_year_id;

-- ---------------------------------------------------------------------------------------------
-- 4. Données rattachées à l'année : rattachées à l'année en cours pour l'existant.
-- ---------------------------------------------------------------------------------------------
alter table fee add column academic_year_id uuid references academic_year (id);
update fee set academic_year_id = (select id from academic_year where active);
-- « Scolarité Maternelle » revient chaque année : le nom est unique par année, plus pour toujours.
alter table fee drop constraint fee_name_establishment_id_key;
alter table fee add constraint uk_fee_establishment_year_name unique (establishment_id, academic_year_id, name);
create index idx_fee_year on fee (academic_year_id);

alter table activity add column academic_year_id uuid references academic_year (id);
update activity set academic_year_id = (select id from academic_year where active);
alter table activity drop constraint uk_activity_establishment_name;
alter table activity add constraint uk_activity_establishment_year_name unique (establishment_id, academic_year_id, name);

alter table school_event add column academic_year_id uuid references academic_year (id);
update school_event set academic_year_id = (select id from academic_year where active);
create index idx_school_event_year on school_event (academic_year_id);

-- Une classe existe une fois ; elle est ouverte pour une année. Préparer l'année suivante
-- rouvrira les classes de l'année passée, sans rien recréer.
create table school_class_year
(
    id               uuid primary key,
    created_at       timestamp(6) with time zone,
    updated_at       timestamp(6) with time zone,
    created_by       varchar(255),
    modified_by      varchar(255),
    school_class_id  uuid not null references school_class (id) on delete cascade,
    academic_year_id uuid not null references academic_year (id),
    constraint uk_school_class_year unique (school_class_id, academic_year_id)
);
insert into school_class_year (id, created_at, updated_at, created_by, modified_by, school_class_id, academic_year_id)
select gen_random_uuid(), now(), now(), 'migration-V35', 'migration-V35', c.id, y.id
  from school_class c
  cross join academic_year y
 where y.active;

-- Les écoles qui n'avaient pas déclaré l'année en cours reçoivent leur année d'école.
insert into school_year (id, created_at, updated_at, created_by, modified_by, establishment_id, academic_year_id)
select gen_random_uuid(), now(), now(), 'migration-V35', 'migration-V35', e.id, y.id
  from establishment e
  cross join academic_year y
 where y.active
   and not exists (select 1 from school_year sy where sy.establishment_id = e.id and sy.academic_year_id = y.id);

-- ---------------------------------------------------------------------------------------------
-- 5. Année consultée, propre à chaque utilisateur ; nulle, c'est l'année en cours.
-- ---------------------------------------------------------------------------------------------
alter table users add column consulted_academic_year_id uuid references academic_year (id) on delete set null;
