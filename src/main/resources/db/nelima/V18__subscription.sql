-- Abonnement des écoles à Nelima.
--
-- YPYit a deux sources de revenu : la commission de 2 % sur les paiements en ligne, tracée depuis
-- toujours transaction par transaction, et l'abonnement annuel — qui n'existait nulle part. Il se
-- facturait hors outil, donc de mémoire.

-- La formule est stockée et non déduite de l'effectif : Enterprise se négocie sur devis, et une
-- école peut obtenir un tarif consenti. L'effectif ne fait que la proposer.
alter table establishment add column subscription_plan varchar(24);
alter table establishment add column subscribed_at date;

-- Une facture est une pièce, pas une vue : la formule et le montant y sont recopiés à l'émission
-- et n'en bougent plus. Renégocier un tarif ne doit pas réécrire les factures déjà émises.
--
-- Aucune colonne de statut : « payée » se lit à paid_at, « en retard » se déduit de due_at et de la
-- date du jour. Un statut stocké supposerait un travail programmé pour le tenir à jour, et un
-- statut périmé est pire que pas de statut.
create table subscription_invoice (
    id                  uuid primary key,
    created_at          timestamp(6) with time zone,
    updated_at          timestamp(6) with time zone,
    created_by          varchar(255),
    modified_by         varchar(255),
    number              varchar(24)  not null,
    plan                varchar(24)  not null,
    amount              numeric(19, 2) not null,
    period_start        date         not null,
    period_end          date         not null,
    issued_at           timestamp(6) with time zone not null,
    due_at              date         not null,
    paid_at             timestamp(6) with time zone,
    payment_method      varchar(64),
    payment_reference   varchar(120),
    -- Une facture annulée est conservée : retirer une ligne ferait un trou dans la suite comptable,
    -- qu'aucun contrôle ne saurait ensuite expliquer.
    cancelled           boolean      not null default false,
    cancellation_reason varchar(255),
    establishment_id    uuid         not null references establishment (id),
    constraint uk_subscription_invoice_number unique (number),
    -- Une seule facture par école et par période : émettre deux fois la même année doublerait la
    -- dette de l'école sans que personne ne s'en aperçoive.
    constraint uk_subscription_invoice_period unique (establishment_id, period_start)
);

create index idx_subscription_invoice_establishment
    on subscription_invoice (establishment_id, period_start desc);

-- Compteur verrouillé plutôt qu'une séquence : une séquence laisse des trous en cas d'annulation de
-- transaction, ce qu'une numérotation comptable ne tolère pas.
create table subscription_invoice_counter (
    id            uuid primary key,
    created_at    timestamp(6) with time zone,
    updated_at    timestamp(6) with time zone,
    created_by    varchar(255),
    modified_by   varchar(255),
    year          integer not null,
    last_sequence bigint  not null default 0,
    constraint uk_subscription_invoice_counter_year unique (year)
);
