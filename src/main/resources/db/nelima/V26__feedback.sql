-- Ce qu'un parent a à nous dire.
--
-- Une suggestion déposée dans le vide vaut moins que pas de champ du tout : le parent croit avoir
-- été entendu. La table existe donc autant pour tracer que pour permettre la relecture, et un
-- courriel part à YPYit à chaque dépôt.

create table feedback
(
    id          uuid                     not null primary key,
    user_id     uuid                     not null references users (id) on delete cascade,
    message     varchar(2000)            not null,
    status      varchar(32)              not null,
    -- Version de l'application au moment du dépôt : c'est la première chose que le support
    -- demande, et la demander au parent après coup est le meilleur moyen de ne jamais l'obtenir.
    app_version varchar(32),
    created_at  timestamp with time zone,
    updated_at  timestamp with time zone,
    created_by  varchar(255),
    modified_by varchar(255)
);

create index idx_feedback_created_at on feedback (created_at desc);
create index idx_feedback_user on feedback (user_id);
