-- Emploi du temps d'une classe : une semaine type, faite de créneaux qui se répètent.
--
-- Pas de date : une école ivoirienne fixe sa grille à la rentrée et la tient toute l'année. Les
-- exceptions (examens, congés) relèvent du calendrier de l'établissement, qui existe déjà.
create table timetable_slot
(
    id              uuid primary key,
    created_at      timestamp(6) with time zone,
    updated_at      timestamp(6) with time zone,
    created_by      varchar(255),
    modified_by     varchar(255),
    -- La grille n'a pas de sens sans sa classe : elle part avec elle.
    school_class_id uuid         not null references school_class (id) on delete cascade,
    day_of_week     varchar(16)  not null,
    start_time      time         not null,
    end_time        time         not null,
    subject         varchar(96)  not null,
    -- Un enseignant qui quitte l'école laisse le créneau à pourvoir, il ne l'efface pas.
    teacher_id      uuid references staff (id) on delete set null,
    room            varchar(64),
    constraint ck_timetable_slot_order check (start_time < end_time)
);

create index idx_timetable_slot_class on timetable_slot (school_class_id, day_of_week, start_time);
create index idx_timetable_slot_teacher on timetable_slot (teacher_id, day_of_week);
