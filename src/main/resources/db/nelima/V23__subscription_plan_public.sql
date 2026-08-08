-- Ce que le site public montre, et laquelle des formules il met en avant.
--
-- Deux drapeaux distincts, et non un seul « visible » avec un rang : toutes les formules ne sont pas
-- publiables — une offre négociée pour un réseau, un tarif de lancement réservé aux pilotes n'ont
-- rien à faire sur une page ouverte — et parmi celles qui le sont, une seule est mise en avant.
--
-- La mise en avant n'est pas garantie unique par un index : basculer d'une formule à l'autre dans
-- la même transaction violerait la contrainte avant la fin de l'écriture. C'est le service qui la
-- tient, comme pour l'année scolaire active d'un établissement.

alter table subscription_plan add column public boolean not null default false;
alter table subscription_plan add column featured boolean not null default false;

-- Reprise de l'état affiché aujourd'hui par nelima.ci : les trois premiers paliers y figurent,
-- Standard y est mis en avant, et Enterprise n'y a jamais été montré — il se négocie.
update subscription_plan set public = true where code in ('DECOUVERTE', 'STANDARD', 'PRO');
update subscription_plan set featured = true where code = 'STANDARD';
