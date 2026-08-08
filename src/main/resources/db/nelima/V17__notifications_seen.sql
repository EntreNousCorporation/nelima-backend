-- Tout l'état « lu » des notifications tient dans cette colonne : le flux se recalcule des données
-- existantes (inscriptions venues des familles, encaissements sans reçu, échéances échues), et lui
-- consacrer une table n'y écrirait qu'un compteur de badge — une seconde vérité à tenir d'accord
-- avec la première.
alter table users add column notifications_seen_at timestamp(6) with time zone;
