-- Deux champs que l'école ne pouvait pas renseigner, alors qu'ils figurent sur ses documents.
--
-- Le sigle sert là où le nom complet ne tient pas : reçus, SMS, en-têtes. Le numéro d'agrément est
-- celui du ministère ; le stocker évite qu'un secrétariat le recopie de mémoire sur chaque pièce.
alter table establishment add column short_name varchar(32);
alter table establishment add column accreditation_number varchar(64);
