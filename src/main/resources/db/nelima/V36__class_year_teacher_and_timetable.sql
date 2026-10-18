-- Le titulaire et l'emploi du temps sont ceux d'une année.
--
-- Une classe garde son identité d'une année sur l'autre ; son titulaire, lui, change avec les
-- affectations, et sa grille avec la rentrée. Les garder sur la classe ferait réécrire l'année
-- passée chaque fois qu'on prépare la suivante.
alter table school_class_year add column main_teacher_id uuid references staff (id) on delete set null;
update school_class_year cy
   set main_teacher_id = c.main_teacher_id
  from school_class c
 where c.id = cy.school_class_id;

alter table timetable_slot add column academic_year_id uuid references academic_year (id);
update timetable_slot set academic_year_id = (select id from academic_year where active);
create index idx_timetable_slot_year on timetable_slot (school_class_id, academic_year_id);
