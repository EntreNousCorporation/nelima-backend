-- Habilitations réglables par école : pour un rôle d'équipe (directeur, comptable, secrétariat),
-- une école peut définir ses propres permissions. Sans réglage, le rôle garde celles de la
-- plateforme ; l'administrateur d'établissement garde toujours tout.

create table establishment_role
(
    id               uuid primary key,
    created_at       timestamp(6) with time zone,
    updated_at       timestamp(6) with time zone,
    created_by       varchar(255),
    modified_by      varchar(255),
    establishment_id uuid not null references establishment (id) on delete cascade,
    role_id          uuid not null references role (id),
    constraint uk_establishment_role unique (establishment_id, role_id)
);

create table establishment_role_permissions
(
    establishment_role_id uuid not null references establishment_role (id) on delete cascade,
    permission_id         uuid not null references permission (id) on delete cascade,
    primary key (establishment_role_id, permission_id)
);
