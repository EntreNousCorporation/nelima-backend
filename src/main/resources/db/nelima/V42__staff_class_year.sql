-- Les enseignants d'une classe changent d'une année à l'autre : le rattachement devient annuel.
-- Les rattachements existants sont reportés sur l'année en cours (à défaut, la plus récente).

create table staff_class_year
(
    id               uuid primary key,
    created_at       timestamp(6) with time zone,
    updated_at       timestamp(6) with time zone,
    created_by       varchar(255),
    modified_by      varchar(255),
    staff_id         uuid not null references staff (id) on delete cascade,
    school_class_id  uuid not null references school_class (id) on delete cascade,
    academic_year_id uuid not null references academic_year (id),
    constraint uk_staff_class_year unique (staff_id, school_class_id, academic_year_id)
);
create index idx_staff_class_year_class on staff_class_year (academic_year_id, school_class_id);

insert into staff_class_year (id, created_at, staff_id, school_class_id, academic_year_id)
select gen_random_uuid(), now(), sc.staff_id, sc.school_class_id, y.id
from staff_class sc
         cross join (select id from academic_year order by active desc, start_date desc limit 1) y;

drop table staff_class;
