-- Les classes manquaient : l'école ne disposait que des niveaux, qui disent ce qu'on enseigne et
-- non qui est assis avec qui. Le secrétariat, lui, travaille par classe.
create table school_class (
    id                uuid primary key,
    created_at        timestamp(6) with time zone,
    updated_at        timestamp(6) with time zone,
    created_by        varchar(255),
    modified_by       varchar(255),
    name              varchar(64)  not null,
    room              varchar(64),
    capacity          integer      not null,
    main_teacher_name varchar(128),
    level_of_study_id uuid references level_of_study (id),
    establishment_id  uuid         not null references establishment (id),
    constraint uk_school_class_establishment_name unique (establishment_id, name)
);

create index idx_school_class_establishment on school_class (establishment_id);

-- Affectation facultative : un élève inscrit en cours d'année attend souvent d'être réparti.
alter table student add column school_class_id uuid references school_class (id);
create index idx_student_school_class on student (school_class_id);
