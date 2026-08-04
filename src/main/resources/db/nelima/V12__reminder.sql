-- Le rappel d'échéance partait sans laisser de trace. C'était assumé tant que le seul émetteur
-- était le travail programmé : le pire qu'on risquait était un doublon de notification, gratuit.
-- Avec des campagnes lancées à la main et le SMS facturé à l'envoi, ce registre devient la seule
-- chose qui empêche de relancer deux fois la même famille le même jour — et de le payer deux fois.

create table reminder_campaign (
    id               uuid primary key,
    created_at       timestamp(6) with time zone,
    updated_at       timestamp(6) with time zone,
    created_by       varchar(255),
    modified_by      varchar(255),
    name             varchar(128) not null,
    target           varchar(32)  not null,
    message_template varchar(500) not null,
    sent_at          timestamp(6) with time zone not null,
    -- Rappels effectivement partis, et destinataires écartés parce que déjà relancés du jour.
    -- Les deux comptent : une campagne qui n'envoie rien doit le dire, pas paraître réussie.
    sent_count       integer      not null default 0,
    skipped_count    integer      not null default 0,
    establishment_id uuid         not null references establishment (id)
);

create index idx_reminder_campaign_establishment on reminder_campaign (establishment_id, sent_at desc);

create table reminder_campaign_channel (
    campaign_id uuid        not null references reminder_campaign (id) on delete cascade,
    channel     varchar(16) not null,
    primary key (campaign_id, channel)
);

create table reminder_delivery (
    id             uuid primary key,
    created_at     timestamp(6) with time zone,
    updated_at     timestamp(6) with time zone,
    created_by     varchar(255),
    modified_by    varchar(255),
    channel        varchar(16) not null,
    origin         varchar(16) not null,
    -- La journée, et non l'horodatage : deux envois à quelques minutes d'intervalle sont un
    -- doublon, deux envois à une semaine d'intervalle ne le sont pas.
    sent_on        date        not null,
    sent_at        timestamp(6) with time zone not null,
    installment_id uuid        not null references installment (id) on delete cascade,
    recipient_id   uuid        not null references users (id) on delete cascade,
    campaign_id    uuid references reminder_campaign (id) on delete set null,
    -- C'est cette contrainte, et non le code, qui rend le doublon impossible : deux envois
    -- concurrents passeraient une simple vérification préalable.
    constraint uk_reminder_delivery_day unique (installment_id, recipient_id, channel, sent_on)
);

create index idx_reminder_delivery_campaign on reminder_delivery (campaign_id);
create index idx_reminder_delivery_installment on reminder_delivery (installment_id);
