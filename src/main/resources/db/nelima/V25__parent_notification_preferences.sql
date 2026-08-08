-- Ce qu'un parent accepte de recevoir.
--
-- La table `notification_preference` existante dit ce que l'ÉCOLE accepte d'envoyer à ses familles :
-- sa clé est (establishment_id, event, channel) et son service refuse un appelant qui n'est pas un
-- établissement. Elle ne répond donc pas à la question posée ici, qui est l'autre moitié du sujet.
--
-- Les deux se croisent par un ET au moment de l'envoi : l'école autorise, et le parent accepte.
-- Aucune des deux ne peut forcer l'autre.

create table user_notification_preference
(
    id         uuid                     not null primary key,
    user_id    uuid                     not null references users (id) on delete cascade,
    event      varchar(64)              not null,
    channel    varchar(32)              not null,
    enabled    boolean                  not null,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by  varchar(255),
    modified_by varchar(255),
    constraint uk_user_notification_preference unique (user_id, event, channel)
);

-- Matrice creuse, comme celle de l'école : une ligne n'existe que si l'interrupteur a été touché.
-- Un parent qui n'a jamais ouvert l'écran retombe sur le défaut de l'événement, et ne perd rien.
create index idx_user_notification_preference_user on user_notification_preference (user_id);

-- Heures calmes.
--
-- Elles TAISENT la notification, elles ne suppriment pas l'information : le fil de notifications
-- est déduit de faits datés, rien n'y est stocké. Un rappel tu à 23h reste dans le fil et compte
-- dans la pastille au réveil. C'est ce qui permet de se passer d'une file d'attente — et un rappel
-- d'échéance purement supprimé, c'est un impayé.
--
-- Nulles par défaut : personne n'est mis en silence sans l'avoir demandé.
alter table users add column quiet_from time;
alter table users add column quiet_to time;
