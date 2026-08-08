-- Ville de l'établissement.
--
-- Distincte de l'adresse : `address.name` est un libellé postal libre — « Rue des Jardins, Cocody II
-- Plateaux — Abidjan » — utile sur un courrier, illisible dans une liste de parc. En extraire la
-- ville par découpage serait faux dès la première saisie qui ne suit pas la même forme.
--
-- Une colonne, donc, et non une table de villes : rien ne se rattache à une ville aujourd'hui, et un
-- référentiel sans consommateur est une table à tenir à jour pour personne.

alter table establishment add column city varchar(120);
