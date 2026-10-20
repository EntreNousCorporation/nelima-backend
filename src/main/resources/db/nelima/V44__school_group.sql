-- Groupes scolaires à plusieurs sites : chaque site reste un établissement à part entière (élèves,
-- classes, frais, caisse), rattaché à un groupe. La direction du groupe passe d'un site à l'autre ;
-- les équipes de site ne voient que le leur.

create table school_group
(
    id          uuid primary key,
    created_at  timestamp(6) with time zone,
    updated_at  timestamp(6) with time zone,
    created_by  varchar(255),
    modified_by varchar(255),
    name        varchar(120) not null unique
);

alter table establishment add column school_group_id uuid references school_group (id) on delete set null;
create index idx_establishment_school_group on establishment (school_group_id);

-- Direction de groupe : peut afficher n'importe quel site de son groupe.
alter table users add column group_wide boolean not null default false;
-- Site affiché par une direction de groupe ; nul : son propre site.
alter table users add column consulted_establishment_id uuid references establishment (id) on delete set null;
