-- Une facture annulée libère sa période.
--
-- La contrainte posée en V18 portait sur (establishment_id, period_start) sans regarder
-- l'annulation. Elle rendait donc l'annulation inutilisable : une facture émise par erreur — mauvaise
-- formule, mauvaise école — était annulée puis sa période restait bloquée à jamais, sans qu'aucune
-- facture correcte ne puisse la remplacer. Autant ne pas offrir l'annulation.
--
-- L'index partiel dit ce qu'on voulait vraiment : une seule facture *vivante* par période. Les
-- factures annulées s'accumulent sans se gêner — elles gardent leur numéro, la suite reste continue,
-- et l'historique montre les deux tentatives, ce qui est exactement ce qu'un contrôle attend.

alter table subscription_invoice
    drop constraint if exists uk_subscription_invoice_period;

create unique index if not exists uk_subscription_invoice_period
    on subscription_invoice (establishment_id, period_start)
    where cancelled = false;
