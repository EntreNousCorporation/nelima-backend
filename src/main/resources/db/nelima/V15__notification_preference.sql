-- Ce que l'école accepte d'envoyer à ses familles, par événement et par canal.
-- L'absence de ligne vaut la valeur par défaut de l'événement : notification et courriel ouverts,
-- SMS fermé, parce qu'il se facture et qu'un canal payant activé sans le savoir se découvre sur
-- la facture.
create table notification_preference (
    id               uuid primary key,
    created_at       timestamp(6) with time zone,
    updated_at       timestamp(6) with time zone,
    created_by       varchar(255),
    modified_by      varchar(255),
    event            varchar(48) not null,
    channel          varchar(16) not null,
    enabled          boolean     not null,
    establishment_id uuid        not null references establishment (id),
    constraint uk_notification_preference unique (establishment_id, event, channel)
);

create index idx_notification_preference_establishment on notification_preference (establishment_id);
