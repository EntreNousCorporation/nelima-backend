-- Photo courante de l'élève : la dernière pièce « Photo d'identité » de son dossier.
--
-- Portée par l'élève plutôt que recherchée à chaque affichage : une liste de trente élèves
-- coûterait sinon une requête de plus par ligne, rien que pour savoir s'il y a une photo.
alter table student add column photo_document_id uuid references student_document (id) on delete set null;

-- Reprise : les photos déjà versées au dossier deviennent la photo de leur élève.
update student s
   set photo_document_id = latest.id
  from (select distinct on (student_id) id, student_id
          from student_document
         where kind = 'PHOTO'
         order by student_id, created_at desc) as latest
 where latest.student_id = s.id;
