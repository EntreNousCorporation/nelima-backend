-- Dossier de l'élève : les pièces que l'école réclame à l'inscription (extrait de naissance,
-- photo, carnet de vaccination…).
--
-- La base ne garde que la description de la pièce ; le fichier est dans un bucket privé, sous
-- `object_key`. Rien ici n'est une URL : une pièce ne se lit qu'à travers le backend.
create table student_document
(
    id           uuid primary key,
    created_at   timestamp(6) with time zone,
    updated_at   timestamp(6) with time zone,
    created_by   varchar(255),
    modified_by  varchar(255),
    student_id   uuid         not null references student (id) on delete cascade,
    kind         varchar(32)  not null,
    label        varchar(128),
    file_name    varchar(255) not null,
    content_type varchar(128) not null,
    size_bytes   bigint       not null,
    object_key   varchar(255) not null unique
);

create index idx_student_document_student on student_document (student_id);
