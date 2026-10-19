-- Report des impayés d'une année sur la suivante : la tranche d'origine est marquée reportée et
-- pointe vers celle qui la remplace, pour qu'on retrouve toujours d'où vient une dette.

alter table installment drop constraint installment_status_check;
alter table installment add constraint installment_status_check
    check (status in ('PENDING', 'PAID', 'CANCELLED', 'WRITTEN_OFF', 'CARRIED_OVER'));

alter table installment add column carried_to_id uuid references installment (id);
