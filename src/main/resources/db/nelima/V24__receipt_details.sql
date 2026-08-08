-- Ce qu'un parent lit sur son reçu : le motif, la ventilation du montant, la classe de l'enfant et
-- l'opérateur par lequel il a payé. Rien de tout cela n'était conservé.
--
-- Figé sur le reçu plutôt que relu par jointure, comme le canal l'a été en V4 : un reçu est une
-- pièce. Le motif d'une tranche peut être corrigé, un élève change de classe à la rentrée, et le
-- taux de commission de la plateforme évolue — une pièce qui se recalculerait cesserait d'attester
-- ce qui s'est réellement passé le jour du paiement.

alter table receipt add column fee_label varchar(255);
alter table receipt add column student_class_name varchar(64);
alter table receipt add column establishment_name varchar(255);
alter table receipt add column amount_school numeric(38, 2);
alter table receipt add column amount_commission numeric(38, 2);

-- Le motif et la ventilation sont reconstituables : ils vivent encore sur la tranche et sur la
-- tentative, qu'aucun reçu n'a le droit de perdre.
update receipt r
set fee_label = i.label,
    amount_school = pi.amount_school,
    amount_commission = pi.amount_commission
from payment_intent pi
         join installment i on i.id = pi.installment_id
where pi.id = r.payment_intent_id;

-- Le nom de l'école est recopié depuis la relation, qui existe déjà sur le reçu. Un établissement
-- change rarement de nom, et le sien d'aujourd'hui reste la meilleure approximation de celui
-- d'hier. À partir de maintenant il sera figé à l'émission, comme le reste.
update receipt r
set establishment_name = e.name
from establishment e
where e.id = r.establishment_id;

-- La classe, elle, ne l'est pas : celle d'aujourd'hui n'est pas celle du jour du paiement, et la
-- recopier serait antidater une information. Les reçus antérieurs restent donc sans classe, et
-- l'application n'affiche pas la ligne plutôt que d'en inventer une.

-- L'opérateur choisi par la famille — « Orange Money », « Wave » — était transmis à l'agrégateur
-- sans jamais être écrit. Il l'est désormais sur la tentative, dès l'initiation.
alter table payment_intent add column payment_method varchar(32);

-- Puis recopié sur le reçu à l'émission, au même titre que le canal.
alter table receipt add column payment_method varchar(32);

-- Aucune reprise possible pour les paiements passés : la donnée n'a jamais existé de ce côté, et
-- la réclamer à l'agrégateur tentative par tentative n'en vaut pas le prix. Les reçus déjà émis
-- resteront sans opérateur, et l'application omet la ligne.
