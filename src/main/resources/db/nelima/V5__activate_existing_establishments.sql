-- Le drapeau d'activité n'était écrit nulle part : tous les établissements existants valaient
-- faux, y compris ceux qui encaissent. Rien ne le lisait jusqu'ici, et rien ne permet encore de
-- suspendre un établissement — aucune valeur voulue n'est donc écrasée par cette reprise.
update establishment set active = true where active = false;
