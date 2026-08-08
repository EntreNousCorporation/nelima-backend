-- Les formules deviennent une donnée.
--
-- Jusqu'ici le palier et le libellé vivaient dans une énumération Java, seul le tarif étant en base.
-- Le raisonnement tenait tant que la grille était figée : un tarif se renégocie souvent, un palier
-- presque jamais. Il ne tient plus dès qu'on veut créer une formule — une offre de lancement, un
-- contrat cadre pour un réseau — sans passer par une livraison.
--
-- Le code reste la clé métier : c'est lui qui est déjà écrit dans `establishment.subscription_plan`
-- et dans `subscription_invoice.plan`, et le conserver évite de réécrire ces colonnes.

create table subscription_plan
(
    id           uuid           not null primary key,
    created_at   timestamp(6) with time zone,
    updated_at   timestamp(6) with time zone,
    created_by   varchar(255),
    modified_by  varchar(255),

    code         varchar(32)    not null,
    label        varchar(80)    not null,
    description  varchar(160),

    -- Plafond d'effectif. Nul = sans plafond : c'est le palier de tête, celui qui se négocie.
    max_students integer,
    price        numeric(19, 2) not null,

    -- On désactive, on ne supprime pas, dès qu'une école ou une facture s'y réfère : une formule
    -- effacée laisserait des factures dont le palier ne veut plus rien dire.
    active       boolean        not null default true,
    position     integer        not null default 0,

    constraint uk_subscription_plan_code unique (code)
);

-- Reprise de la grille en place. Les tarifs déjà réglés depuis la console vivent dans
-- `global_parameter` : les relire ici évite de rétablir en silence les valeurs du business plan.
insert into subscription_plan (id, created_at, code, label, description, max_students, price, position)
values (gen_random_uuid(), now(), 'DECOUVERTE', 'Découverte', 'Jusqu''à 100 élèves', 100,
        coalesce((select nullif(trim(value), '')::numeric from global_parameter
                  where code = 'SUBSCRIPTION_PRICE_DECOUVERTE'), 75000), 1),
       (gen_random_uuid(), now(), 'STANDARD', 'Standard', 'Jusqu''à 400 élèves', 400,
        coalesce((select nullif(trim(value), '')::numeric from global_parameter
                  where code = 'SUBSCRIPTION_PRICE_STANDARD'), 200000), 2),
       (gen_random_uuid(), now(), 'PRO', 'Pro', 'Jusqu''à 1 000 élèves', 1000,
        coalesce((select nullif(trim(value), '')::numeric from global_parameter
                  where code = 'SUBSCRIPTION_PRICE_PRO'), 400000), 3),
       (gen_random_uuid(), now(), 'ENTERPRISE', 'Enterprise', 'Au-delà de 1 000 élèves · sur devis', null,
        coalesce((select nullif(trim(value), '')::numeric from global_parameter
                  where code = 'SUBSCRIPTION_PRICE_ENTERPRISE'), 600000), 4);

-- Le libellé est figé sur la facture, comme le montant l'est déjà. Une formule renommée ou retirée
-- ne doit pas réécrire une pièce comptable émise sous l'ancien nom.
alter table subscription_invoice add column plan_label varchar(80);

update subscription_invoice i
set plan_label = p.label
from subscription_plan p
where p.code = i.plan;

alter table subscription_invoice alter column plan_label set not null;

-- Volontairement sans clé étrangère : une facture doit rester lisible même si la formule qui l'a
-- produite disparaît un jour. L'établissement, lui, référence une formule vivante.
alter table establishment
    add constraint fk_establishment_subscription_plan
        foreign key (subscription_plan) references subscription_plan (code);
