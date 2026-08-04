-- Une école raisonne par cycle — « combien au primaire » — et non niveau par niveau. Le cycle est
-- une propriété du niveau : le déduire du code dans chaque écran garantirait qu'un jour l'un range
-- la sixième au primaire.
alter table level_of_study add column cycle varchar(32);

update level_of_study set cycle = 'KINDERGARTEN'
 where code in ('PETITE_SECTION', 'MOYENNE_SECTION', 'GRANDE_SECTION');

update level_of_study set cycle = 'PRIMARY'
 where code in ('CP1', 'CP2', 'CE1', 'CE2', 'CM1', 'CM2');

update level_of_study set cycle = 'MIDDLE_SCHOOL'
 where code in ('SIXIEME', 'CINQUIEME', 'QUATRIEME', 'TROISIEME');

update level_of_study set cycle = 'HIGH_SCHOOL'
 where code in ('SECONDE', 'PREMIERE', 'TERMINALE');

-- La colonne reste nullable : un niveau ajouté plus tard au catalogue sans cycle doit pouvoir
-- exister, et l'écran le range alors sous « Autres » plutôt que de le faire disparaître.
