--
-- Payeur déclaré au comptoir.
--
-- Un règlement en espèces est apporté par une famille, mais saisi par un agent. Sans ces deux
-- colonnes, le reçu portait le nom de l'agent et l'email partait à l'établissement : la famille
-- ne recevait rien et ne se reconnaissait pas sur sa propre pièce comptable.
--
-- Nullables : sur un paiement en ligne le payeur est l'utilisateur authentifié, et au guichet
-- l'information reste facultative — sans email, aucun envoi n'est tenté.
--

ALTER TABLE public.payment_intent ADD COLUMN payer_name character varying(255);
ALTER TABLE public.payment_intent ADD COLUMN payer_email character varying(320);
