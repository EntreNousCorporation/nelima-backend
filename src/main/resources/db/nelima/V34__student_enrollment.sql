-- Inscription annuelle : un élève est inscrit pour une année scolaire, avec la classe et le niveau
-- de cette année.
--
-- L'élève garde son identité d'une année sur l'autre (matricule, état civil, dossier, parents) ;
-- c'est son inscription qui change. Sans elle, réinscrire un élève en septembre écraserait sa
-- classe de l'an passé, et l'école ne pourrait plus dire combien d'élèves elle avait l'an dernier.
create table student_enrollment
(
    id               uuid primary key,
    created_at       timestamp(6) with time zone,
    updated_at       timestamp(6) with time zone,
    created_by       varchar(255),
    modified_by      varchar(255),
    student_id       uuid not null references student (id) on delete cascade,
    -- Pas de cascade : une année qui a des inscrits ne se supprime pas, le service le refuse.
    academic_year_id uuid not null references academic_year (id),
    school_class_id  uuid references school_class (id) on delete set null,
    level_of_study_id uuid references level_of_study (id),
    constraint uk_student_enrollment_year unique (student_id, academic_year_id)
);

create index idx_student_enrollment_year on student_enrollment (academic_year_id);

-- Reprise : les élèves actuels sont inscrits dans l'année en cours de leur école, avec leur classe
-- et leur niveau d'aujourd'hui. Une école sans année déclarée n'a pas d'inscription à reprendre.
insert into student_enrollment (id, created_at, updated_at, created_by, modified_by,
                                student_id, academic_year_id, school_class_id, level_of_study_id)
select gen_random_uuid(), now(), now(), 'migration-V34', 'migration-V34',
       s.id, y.id, s.school_class_id, s.level_of_study_id
  from student s
  join academic_year y on y.establishment_id = s.establishment_id and y.active;
