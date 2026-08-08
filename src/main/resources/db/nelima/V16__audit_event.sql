-- Journal chronologique et transverse des actes sensibles.
-- L'audit JPA existant (created_by / modified_by) dit qui a touché un objet, mais il faut ouvrir
-- chaque objet pour le lire. Une direction qui demande « qu'a-t-on fait cette semaine ? » n'a pas
-- de réponse sans ce journal.
create table audit_event (
    id               uuid primary key,
    created_at       timestamp(6) with time zone,
    updated_at       timestamp(6) with time zone,
    created_by       varchar(255),
    modified_by      varchar(255),
    action           varchar(48) not null,
    occurred_at      timestamp(6) with time zone not null,
    -- Nom recopié au moment des faits : un compte fermé ou renommé ne doit pas effacer qui a
    -- encaissé.
    actor_name       varchar(160),
    target           varchar(255),
    details          varchar(500),
    actor_id         uuid references users (id),
    establishment_id uuid not null references establishment (id)
);

create index idx_audit_event_establishment on audit_event (establishment_id, occurred_at desc);
