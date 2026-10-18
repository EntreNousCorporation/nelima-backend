-- Rattrapage des tranches jamais générées.
--
-- L'inscription d'un élève, et l'extension d'un frais à un niveau qui compte déjà des élèves,
-- créaient la dette (student_fee) sans ses tranches (installment). Or le solde, l'échéancier et
-- les relances se lisent tous dans les tranches : ces élèves affichaient zéro à régler.
--
-- On produit ce que InstallmentGenerator aurait produit, et seulement pour les dettes qui n'ont
-- aucune tranche : une dette déjà découpée, payée ou non, n'est pas touchée.

-- Frais avec échéancier : une tranche par échéance.
insert into installment (id, created_at, updated_at, created_by, modified_by,
                         student_fee_id, fee_schedule_id, label, amount, due_date, status)
select gen_random_uuid(), now(), now(), 'migration-V32', 'migration-V32',
       sf.id, fs.id, fs.label, fs.amount, fs.due_date, 'PENDING'
  from student_fee sf
  join fee_schedule fs on fs.fee_id = sf.fee_id
 where not exists (select 1 from installment i where i.student_fee_id = sf.id);

-- Frais sans échéancier : dû en une fois, à l'échéance portée par la dette si elle existe.
insert into installment (id, created_at, updated_at, created_by, modified_by,
                         student_fee_id, label, amount, due_date, status)
select gen_random_uuid(), now(), now(), 'migration-V32', 'migration-V32',
       sf.id, f.name, f.price, cast(sf.deadline at time zone 'UTC' as date), 'PENDING'
  from student_fee sf
  join fee f on f.id = sf.fee_id
 where f.price is not null
   and not exists (select 1 from fee_schedule fs where fs.fee_id = f.id)
   and not exists (select 1 from installment i where i.student_fee_id = sf.id);
