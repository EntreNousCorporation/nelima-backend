-- Passage en perte d'une dette irrécouvrable, en deux temps : proposé par la comptabilité,
-- validé (ou refusé) par la direction. La tranche n'est passée en perte qu'à la validation.

alter table installment drop constraint installment_status_check;
alter table installment add constraint installment_status_check
    check (status in ('PENDING', 'PAID', 'CANCELLED', 'WRITTEN_OFF'));

create table debt_write_off
(
    id               uuid primary key,
    created_at       timestamp(6) with time zone,
    updated_at       timestamp(6) with time zone,
    created_by       varchar(255),
    modified_by      varchar(255),
    establishment_id uuid           not null references establishment (id),
    installment_id   uuid           not null references installment (id) on delete cascade,
    amount           numeric(38, 2) not null,
    reason           varchar(500)   not null,
    status           varchar(16)    not null,
    proposed_by_id   uuid           not null references users (id),
    proposed_at      timestamp(6) with time zone not null,
    decided_by_id    uuid references users (id),
    decided_at       timestamp(6) with time zone,
    decision_note    varchar(500)
);
create index idx_debt_write_off_establishment on debt_write_off (establishment_id, status);
-- Une seule demande en cours par tranche.
create unique index uk_debt_write_off_open on debt_write_off (installment_id) where status = 'PROPOSED';
