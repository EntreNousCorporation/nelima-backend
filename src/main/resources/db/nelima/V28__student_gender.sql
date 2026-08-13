-- Le sexe de l'élève, pour la répartition filles/garçons que les écoles doivent déclarer.
--
-- Nullable, et ce n'est pas une facilité : des élèves sont déjà inscrits sans cette information,
-- et la rendre obligatoire rétroactivement supposerait de la deviner. Une école la renseigne au
-- fil de l'eau depuis la fiche de l'élève, ou d'un coup au prochain import.
--
-- Stocké en texte plutôt qu'en type énuméré Postgres : c'est la convention du schéma pour tous
-- les autres énumérés (statut de tranche, rôle du personnel), et un type énuméré natif se
-- modifie mal.
alter table student add column if not exists gender varchar(16);

comment on column student.gender is 'MALE | FEMALE — nul tant que l''école ne l''a pas renseigné';
