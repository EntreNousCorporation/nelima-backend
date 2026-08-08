-- Demandes de démonstration venues du site public.
--
-- La table est la contrepartie d'une décision : le formulaire du site enregistre plutôt que
-- d'envoyer un courriel. Une boîte mail n'est pas un outil de suivi — on n'y sait pas ce qui a été
-- rappelé, et une demande y disparaît sous le reste du courrier.
--
-- Aucune donnée d'élève ici, et aucun compte : c'est une école qui se signale, pas un client.

create table demo_request
(
    id             uuid         not null primary key,
    created_at     timestamp(6) with time zone,
    updated_at     timestamp(6) with time zone,
    created_by     varchar(255),
    modified_by    varchar(255),

    school_name    varchar(160) not null,
    contact_name   varchar(120) not null,
    email          varchar(160) not null,
    phone          varchar(40),
    city           varchar(120),
    -- Effectif déclaré, tel que le visiteur l'annonce. Approximatif par nature : il sert à préparer
    -- l'échange, jamais à facturer.
    student_count  integer,
    message        varchar(2000),

    -- Suivi commercial, volontairement minimal : une demande est en attente, traitée, ou écartée.
    status         varchar(16)  not null default 'PENDING',
    handled_at     timestamp(6) with time zone,
    handled_note   varchar(500),

    -- D'où vient la demande. Utile pour savoir quelle page convertit, sans traceur ni cookie.
    source_page    varchar(120)
);

create index idx_demo_request_created on demo_request (created_at desc);
create index idx_demo_request_status on demo_request (status);
