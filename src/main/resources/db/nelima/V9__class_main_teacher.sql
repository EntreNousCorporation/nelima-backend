-- Le titulaire d'une classe était un nom recopié à la main. Maintenant que le répertoire du
-- personnel existe, il devient une référence : on sait enfin de qui on parle.
alter table school_class add column main_teacher_id uuid references staff (id);

create index idx_school_class_main_teacher on school_class (main_teacher_id);

-- Reprise par rapprochement des noms, uniquement quand elle est sans ambiguïté : un nom qui
-- désigne deux membres du personnel ne se tranche pas tout seul, et se tromper de titulaire est
-- pire que ne pas en désigner. La comparaison ignore la casse et les espaces de bord, les seules
-- variations qu'une saisie manuelle produit de façon systématique.
update school_class sc
   set main_teacher_id = candidate.id
  from (
        select s.id,
               s.establishment_id,
               lower(trim(s.last_name || ' ' || s.first_name))  as reversed,
               lower(trim(s.first_name || ' ' || s.last_name))  as direct
          from staff s
       ) as candidate
 where candidate.establishment_id = sc.establishment_id
   and sc.main_teacher_name is not null
   and lower(trim(sc.main_teacher_name)) in (candidate.reversed, candidate.direct)
   -- Sans ambiguïté : un seul membre de l'établissement doit répondre à ce nom.
   and (select count(*)
          from staff s2
         where s2.establishment_id = sc.establishment_id
           and lower(trim(sc.main_teacher_name)) in (
                 lower(trim(s2.last_name || ' ' || s2.first_name)),
                 lower(trim(s2.first_name || ' ' || s2.last_name)))) = 1;

-- main_teacher_name est conservée. La supprimer perdrait les titulaires qu'aucun membre du
-- répertoire ne recouvre — et à la reprise, le répertoire est vide : toutes les classes existantes
-- sont dans ce cas.
