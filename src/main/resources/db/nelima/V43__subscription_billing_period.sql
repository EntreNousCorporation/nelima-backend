-- Cantine et transport : facturation au mois ou au trimestre, au choix à l'inscription.
alter table canteen_subscription add column billing_period varchar(8) not null default 'MONTHLY';
alter table transport_subscription add column billing_period varchar(8) not null default 'MONTHLY';
