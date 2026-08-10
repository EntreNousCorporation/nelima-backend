-- Les index des chemins chauds.
--
-- Avant cette migration, la base ne portait que DEUX index non uniques sur les tables les plus
-- sollicitées (`idx_payment_intent_status_created_at` et `idx_student_school_class`). Tous les
-- autres index existants sont ceux que PostgreSQL crée implicitement pour une clé primaire ou une
-- contrainte d'unicité — et **PostgreSQL n'indexe pas les clés étrangères automatiquement**.
--
-- Aujourd'hui, à faible volume, le planificateur balaie les tables entières et c'est rapide : c'est
-- exactement ce qui rend le défaut invisible. Il ne se dégrade pas linéairement. Le pire cas est la
-- recherche de reçus d'un parent (`ReceiptService.search`), qui joint sur quatre niveaux
-- `payment_intent → installment → student_fee → student` sans qu'une seule colonne de jointure soit
-- indexée.
--
-- `CREATE INDEX` simple, et non `CONCURRENTLY` : Flyway exécute chaque migration dans une
-- transaction, or `CONCURRENTLY` l'interdit. Au volume actuel la prise de verrou se compte en
-- millisecondes. Le jour où une table dépassera le million de lignes, un index supplémentaire devra
-- passer par une migration hors transaction (`executeInTransaction=false`) — pas par celle-ci.
--
-- `IF NOT EXISTS` partout : la migration doit pouvoir se rejouer sur une base où un index aurait été
-- posé à la main pendant un incident.

-- ── Identité et connexion ────────────────────────────────────────────────────────────────────────

-- `UserRepository.findByPrimaryContact` — `where c.is_primary = true and c.value = :username`.
-- Ce n'est pas une fois par connexion : `CurrentUserProvider.currentUser()` re-résout l'utilisateur
-- à chaque appel, et une seule requête peut en déclencher trois, en plus du filtre JWT.
-- Index partiel : seuls les contacts principaux servent à retrouver un compte.
CREATE INDEX IF NOT EXISTS idx_contact_value_primary
    ON public.contact (value)
    WHERE is_primary = true;

-- `user_contacts` a pour clé primaire (contact_id, user_id) mais n'est jamais traversée dans ce
-- sens : on charge les contacts d'un utilisateur connu. La colonne de queue d'un index composite
-- n'est pas utilisable par préfixe.
CREATE INDEX IF NOT EXISTS idx_user_contacts_user
    ON public.user_contacts (user_id);

-- Même inversion : (contact_id, establishment_id) en clé, mais on charge les contacts d'une école.
CREATE INDEX IF NOT EXISTS idx_establishment_contacts_establishment
    ON public.establishment_contacts (establishment_id);

-- ── Le périmètre du parent ───────────────────────────────────────────────────────────────────────

-- `student_parents` a pour clé primaire (student_id, user_id). Or **toute** requête d'un parent part
-- de `user_id` — `StudentRepository.findByParentUsers_Id`, d'où dépendent `students/mine`, le
-- tableau de bord, les reçus, les échéances, la carte de contact et les préférences de notification.
CREATE INDEX IF NOT EXISTS idx_student_parents_user
    ON public.student_parents (user_id);

-- ── La chaîne de l'argent ────────────────────────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_student_fee_student
    ON public.student_fee (student_id);

CREATE INDEX IF NOT EXISTS idx_student_fee_fee
    ON public.student_fee (fee_id);

CREATE INDEX IF NOT EXISTS idx_installment_student_fee
    ON public.installment (student_fee_id);

CREATE INDEX IF NOT EXISTS idx_installment_fee_schedule
    ON public.installment (fee_schedule_id);

-- `payment_intent.installment_id` ferme la jointure que `TransactionService` et la recherche de
-- reçus remontent depuis le reçu jusqu'à l'élève.
CREATE INDEX IF NOT EXISTS idx_payment_intent_installment
    ON public.payment_intent (installment_id);

-- Le travail nocturne de rappels : `findByStatusAndDueDate…`. L'ordre des colonnes suit la
-- sélectivité — le statut d'abord, qui écarte l'essentiel, la date ensuite pour l'intervalle.
CREATE INDEX IF NOT EXISTS idx_installment_status_due_date
    ON public.installment (status, due_date);

-- ── Le périmètre de l'école ──────────────────────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_student_establishment
    ON public.student (establishment_id);

CREATE INDEX IF NOT EXISTS idx_student_level_of_study
    ON public.student (level_of_study_id);

CREATE INDEX IF NOT EXISTS idx_fee_establishment
    ON public.fee (establishment_id);

CREATE INDEX IF NOT EXISTS idx_school_class_establishment
    ON public.school_class (establishment_id);

CREATE INDEX IF NOT EXISTS idx_staff_establishment
    ON public.staff (establishment_id);

CREATE INDEX IF NOT EXISTS idx_staff_user
    ON public.staff (user_id);

-- Le sélecteur d'école de l'application parent liste les établissements et, pour chacun, demande ses
-- filiales (`findByParent_Id`) — une requête par école, jusqu'à 101 sur une page par défaut. L'index
-- ne supprime pas ce N+1, il en borne le coût unitaire en attendant le correctif applicatif.
CREATE INDEX IF NOT EXISTS idx_establishment_parent
    ON public.establishment (parent_id);

-- ── Journal d'audit ──────────────────────────────────────────────────────────────────────────────

-- Les deux méthodes du dépôt filtrent par établissement et trient par date décroissante.
CREATE INDEX IF NOT EXISTS idx_audit_event_establishment_occurred_at
    ON public.audit_event (establishment_id, occurred_at DESC);
