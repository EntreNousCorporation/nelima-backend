-- Le mode de règlement figure sur la tentative de paiement, pas sur le reçu. Le journal de caisse
-- de l'école doit pourtant distinguer les espèces des chèques et des paiements en ligne : le lire
-- depuis la tentative imposait une requête par ligne affichée. Comme les libellés déjà recopiés
-- sur le reçu, le canal y est figé à l'émission — un reçu est une pièce, il ne se recalcule pas.
alter table receipt add column channel varchar(32);

update receipt r
set channel = pi.channel
from payment_intent pi
where pi.id = r.payment_intent_id;

-- Aucun reçu n'existe sans tentative de paiement : la colonne est donc renseignée partout.
alter table receipt alter column channel set not null;
