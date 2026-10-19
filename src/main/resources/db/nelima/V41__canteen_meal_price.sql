-- Formule cantine au repas : prix d'un repas, facturé au repas servi, regroupé par mois.
-- Nul pour une formule mensuelle.
alter table canteen_formula add column meal_price numeric(38, 2);
