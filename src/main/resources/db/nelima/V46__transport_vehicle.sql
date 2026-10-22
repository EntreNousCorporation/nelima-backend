-- Parc de véhicules d'une école : un car existe d'une année sur l'autre, la ligne ne fait que
-- l'affecter. Assurance et visite technique sont suivies pour prévenir la direction à temps.

create table transport_vehicle
(
    id                         uuid primary key,
    created_at                 timestamp(6) with time zone,
    updated_at                 timestamp(6) with time zone,
    created_by                 varchar(255),
    modified_by                varchar(255),
    establishment_id           uuid        not null references establishment (id) on delete cascade,
    plate                      varchar(16) not null,
    kind                       varchar(16) not null default 'CAR',
    model                      varchar(64),
    capacity                   integer     not null,
    insurance_expiry           date,
    technical_visit            date,
    -- Échéance pour laquelle la direction a déjà été prévenue : une seule alerte par échéance.
    insurance_alerted_for      date,
    technical_visit_alerted_for date,
    active                     boolean     not null default true,
    constraint uk_transport_vehicle_plate unique (establishment_id, plate)
);

alter table transport_line add column vehicle_id uuid references transport_vehicle (id) on delete set null;

-- Les cars déjà saisis sur les lignes deviennent des véhicules du parc.
insert into transport_vehicle (id, created_at, updated_at, establishment_id, plate, kind, capacity)
select gen_random_uuid(), now(), now(), establishment_id, upper(trim(vehicle_plate)), 'CAR', max(capacity)
from transport_line
where vehicle_plate is not null and trim(vehicle_plate) <> ''
group by establishment_id, upper(trim(vehicle_plate));

update transport_line l
set vehicle_id = v.id
from transport_vehicle v
where v.establishment_id = l.establishment_id
  and v.plate = upper(trim(l.vehicle_plate));
