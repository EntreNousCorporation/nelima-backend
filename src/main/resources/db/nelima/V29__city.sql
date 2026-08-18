-- Les villes deviennent un référentiel.
--
-- V20 avait posé une colonne libre et écarté la table, au motif qu'un référentiel sans consommateur
-- est une table à tenir à jour pour personne. Le consommateur existe maintenant : la console ne
-- laisse plus taper une ville, elle la fait choisir. Le raisonnement n'a pas changé, la situation
-- si — et une saisie libre avait déjà produit trois orthographes pour deux villes.
--
-- Le libellé est la clé métier, et non un code comme pour `subscription_plan`. La différence tient
-- à ce que porte la colonne existante : `establishment.subscription_plan` contenait déjà un code, la
-- reprendre par le code n'y touchait pas. `establishment.city` contient un libellé, et c'est ce
-- libellé qui est lu partout — liste du parc, fiche, carte de contact servie aux familles. Le
-- remplacer par un code obligerait chacune de ces lectures à résoudre une jointure pour réafficher
-- ce qui y était déjà écrit. Une ville ne se renomme pas.

create table city
(
    id          uuid         not null primary key,
    created_at  timestamp(6) with time zone,
    updated_at  timestamp(6) with time zone,
    created_by  varchar(255),
    modified_by varchar(255),

    label       varchar(120) not null,

    -- Région administrative, pour regrouper la liste : cent entrées à plat ne se parcourent pas.
    -- Abidjan et Yamoussoukro sont des districts autonomes et portent leur propre nom.
    --
    -- Nulle est permis, et sert aux saisies libres reprises plus bas : leur inventer une région
    -- serait une donnée fausse, et elles ne sont de toute façon plus proposées.
    region      varchar(80),

    -- On désactive, on ne supprime pas : une ville retirée du choix reste lisible sur les écoles
    -- qui la portent. C'est aussi ce qui accueille les saisies libres reprises plus bas.
    active      boolean      not null default true,

    constraint uk_city_label unique (label)
);

-- Abidjan éclatée en communes, et non « Abidjan » seul.
--
-- La ville sert à situer une école dans une liste. La majorité du parc est à Abidjan : une entrée
-- unique y rangerait quatre écoles sur cinq sous le même libellé et ne situerait plus rien. Les
-- treize communes du district — dont Anyama, Bingerville et Songon, qui en font partie — sont
-- préfixées pour rester groupées à la recherche comme au tri.
insert into city (id, created_at, label, region)
values (gen_random_uuid(), now(), 'Abidjan — Abobo', 'Abidjan'),
       (gen_random_uuid(), now(), 'Abidjan — Adjamé', 'Abidjan'),
       (gen_random_uuid(), now(), 'Abidjan — Anyama', 'Abidjan'),
       (gen_random_uuid(), now(), 'Abidjan — Attécoubé', 'Abidjan'),
       (gen_random_uuid(), now(), 'Abidjan — Bingerville', 'Abidjan'),
       (gen_random_uuid(), now(), 'Abidjan — Cocody', 'Abidjan'),
       (gen_random_uuid(), now(), 'Abidjan — Koumassi', 'Abidjan'),
       (gen_random_uuid(), now(), 'Abidjan — Marcory', 'Abidjan'),
       (gen_random_uuid(), now(), 'Abidjan — Plateau', 'Abidjan'),
       (gen_random_uuid(), now(), 'Abidjan — Port-Bouët', 'Abidjan'),
       (gen_random_uuid(), now(), 'Abidjan — Songon', 'Abidjan'),
       (gen_random_uuid(), now(), 'Abidjan — Treichville', 'Abidjan'),
       (gen_random_uuid(), now(), 'Abidjan — Yopougon', 'Abidjan'),

       (gen_random_uuid(), now(), 'Yamoussoukro', 'Yamoussoukro'),
       (gen_random_uuid(), now(), 'Attiégouakro', 'Yamoussoukro'),

       (gen_random_uuid(), now(), 'Agboville', 'Agnéby-Tiassa'),
       (gen_random_uuid(), now(), 'Sikensi', 'Agnéby-Tiassa'),
       (gen_random_uuid(), now(), 'Taabo', 'Agnéby-Tiassa'),
       (gen_random_uuid(), now(), 'Tiassalé', 'Agnéby-Tiassa'),

       (gen_random_uuid(), now(), 'Dabou', 'Grands-Ponts'),
       (gen_random_uuid(), now(), 'Grand-Lahou', 'Grands-Ponts'),
       (gen_random_uuid(), now(), 'Jacqueville', 'Grands-Ponts'),

       (gen_random_uuid(), now(), 'Adzopé', 'La Mé'),
       (gen_random_uuid(), now(), 'Akoupé', 'La Mé'),
       (gen_random_uuid(), now(), 'Alépé', 'La Mé'),
       (gen_random_uuid(), now(), 'Yakassé-Attobrou', 'La Mé'),

       (gen_random_uuid(), now(), 'Abengourou', 'Indénié-Djuablin'),
       (gen_random_uuid(), now(), 'Agnibilékrou', 'Indénié-Djuablin'),
       (gen_random_uuid(), now(), 'Bettié', 'Indénié-Djuablin'),

       (gen_random_uuid(), now(), 'Aboisso', 'Sud-Comoé'),
       (gen_random_uuid(), now(), 'Adiaké', 'Sud-Comoé'),
       (gen_random_uuid(), now(), 'Grand-Bassam', 'Sud-Comoé'),
       (gen_random_uuid(), now(), 'Tiapoum', 'Sud-Comoé'),

       (gen_random_uuid(), now(), 'Buyo', 'Nawa'),
       (gen_random_uuid(), now(), 'Guéyo', 'Nawa'),
       (gen_random_uuid(), now(), 'Méagui', 'Nawa'),
       (gen_random_uuid(), now(), 'Soubré', 'Nawa'),

       (gen_random_uuid(), now(), 'San-Pédro', 'San-Pédro'),
       (gen_random_uuid(), now(), 'Tabou', 'San-Pédro'),

       (gen_random_uuid(), now(), 'Fresco', 'Gbôklé'),
       (gen_random_uuid(), now(), 'Sassandra', 'Gbôklé'),

       (gen_random_uuid(), now(), 'Gbéléban', 'Kabadougou'),
       (gen_random_uuid(), now(), 'Madinani', 'Kabadougou'),
       (gen_random_uuid(), now(), 'Odienné', 'Kabadougou'),
       (gen_random_uuid(), now(), 'Samatiguila', 'Kabadougou'),
       (gen_random_uuid(), now(), 'Séguélon', 'Kabadougou'),

       (gen_random_uuid(), now(), 'Kaniasso', 'Folon'),
       (gen_random_uuid(), now(), 'Minignan', 'Folon'),

       (gen_random_uuid(), now(), 'Gagnoa', 'Gôh'),
       (gen_random_uuid(), now(), 'Oumé', 'Gôh'),

       (gen_random_uuid(), now(), 'Divo', 'Lôh-Djiboua'),
       (gen_random_uuid(), now(), 'Guitry', 'Lôh-Djiboua'),
       (gen_random_uuid(), now(), 'Lakota', 'Lôh-Djiboua'),

       (gen_random_uuid(), now(), 'Didiévi', 'Bélier'),
       (gen_random_uuid(), now(), 'Djékanou', 'Bélier'),
       (gen_random_uuid(), now(), 'Tiébissou', 'Bélier'),
       (gen_random_uuid(), now(), 'Toumodi', 'Bélier'),

       (gen_random_uuid(), now(), 'Daoukro', 'Iffou'),
       (gen_random_uuid(), now(), 'M''Bahiakro', 'Iffou'),
       (gen_random_uuid(), now(), 'Prikro', 'Iffou'),

       (gen_random_uuid(), now(), 'Bocanda', 'N''Zi'),
       (gen_random_uuid(), now(), 'Dimbokro', 'N''Zi'),
       (gen_random_uuid(), now(), 'Kouassi-Kouassikro', 'N''Zi'),

       (gen_random_uuid(), now(), 'Arrah', 'Moronou'),
       (gen_random_uuid(), now(), 'Bongouanou', 'Moronou'),
       (gen_random_uuid(), now(), 'M''Batto', 'Moronou'),

       (gen_random_uuid(), now(), 'Bloléquin', 'Cavally'),
       (gen_random_uuid(), now(), 'Guiglo', 'Cavally'),
       (gen_random_uuid(), now(), 'Taï', 'Cavally'),
       (gen_random_uuid(), now(), 'Toulépleu', 'Cavally'),

       (gen_random_uuid(), now(), 'Bangolo', 'Guémon'),
       (gen_random_uuid(), now(), 'Duékoué', 'Guémon'),
       (gen_random_uuid(), now(), 'Facobly', 'Guémon'),
       (gen_random_uuid(), now(), 'Kouibly', 'Guémon'),

       (gen_random_uuid(), now(), 'Biankouma', 'Tonkpi'),
       (gen_random_uuid(), now(), 'Danané', 'Tonkpi'),
       (gen_random_uuid(), now(), 'Man', 'Tonkpi'),
       (gen_random_uuid(), now(), 'Sipilou', 'Tonkpi'),
       (gen_random_uuid(), now(), 'Zouan-Hounien', 'Tonkpi'),

       (gen_random_uuid(), now(), 'Daloa', 'Haut-Sassandra'),
       (gen_random_uuid(), now(), 'Issia', 'Haut-Sassandra'),
       (gen_random_uuid(), now(), 'Vavoua', 'Haut-Sassandra'),
       (gen_random_uuid(), now(), 'Zoukougbeu', 'Haut-Sassandra'),

       (gen_random_uuid(), now(), 'Bouaflé', 'Marahoué'),
       (gen_random_uuid(), now(), 'Sinfra', 'Marahoué'),
       (gen_random_uuid(), now(), 'Zuénoula', 'Marahoué'),

       (gen_random_uuid(), now(), 'Dikodougou', 'Poro'),
       (gen_random_uuid(), now(), 'Korhogo', 'Poro'),
       (gen_random_uuid(), now(), 'M''Bengué', 'Poro'),
       (gen_random_uuid(), now(), 'Sinématiali', 'Poro'),

       (gen_random_uuid(), now(), 'Ferkessédougou', 'Tchologo'),
       (gen_random_uuid(), now(), 'Kong', 'Tchologo'),
       (gen_random_uuid(), now(), 'Ouangolodougou', 'Tchologo'),

       (gen_random_uuid(), now(), 'Boundiali', 'Bagoué'),
       (gen_random_uuid(), now(), 'Kouto', 'Bagoué'),
       (gen_random_uuid(), now(), 'Tengréla', 'Bagoué'),

       (gen_random_uuid(), now(), 'Béoumi', 'Gbêkê'),
       (gen_random_uuid(), now(), 'Botro', 'Gbêkê'),
       (gen_random_uuid(), now(), 'Bouaké', 'Gbêkê'),
       (gen_random_uuid(), now(), 'Sakassou', 'Gbêkê'),

       (gen_random_uuid(), now(), 'Dabakala', 'Hambol'),
       (gen_random_uuid(), now(), 'Katiola', 'Hambol'),
       (gen_random_uuid(), now(), 'Niakaramandougou', 'Hambol'),

       (gen_random_uuid(), now(), 'Dianra', 'Béré'),
       (gen_random_uuid(), now(), 'Kounahiri', 'Béré'),
       (gen_random_uuid(), now(), 'Mankono', 'Béré'),

       (gen_random_uuid(), now(), 'Koro', 'Bafing'),
       (gen_random_uuid(), now(), 'Ouaninou', 'Bafing'),
       (gen_random_uuid(), now(), 'Touba', 'Bafing'),

       (gen_random_uuid(), now(), 'Kani', 'Worodougou'),
       (gen_random_uuid(), now(), 'Séguéla', 'Worodougou'),

       (gen_random_uuid(), now(), 'Bouna', 'Bounkani'),
       (gen_random_uuid(), now(), 'Doropo', 'Bounkani'),
       (gen_random_uuid(), now(), 'Nassian', 'Bounkani'),
       (gen_random_uuid(), now(), 'Téhini', 'Bounkani'),

       (gen_random_uuid(), now(), 'Bondoukou', 'Gontougo'),
       (gen_random_uuid(), now(), 'Koun-Fao', 'Gontougo'),
       (gen_random_uuid(), now(), 'Sandégué', 'Gontougo'),
       (gen_random_uuid(), now(), 'Tanda', 'Gontougo'),
       (gen_random_uuid(), now(), 'Transua', 'Gontougo');

-- Reprise des saisies libres déjà en base.
--
-- Trois passes, dans cet ordre. Les espaces d'abord — une ville saisie avec une espace de fin n'est
-- pas une autre ville, et la laisser telle quelle créerait un doublon au moment de reprendre ce qui
-- reste. La casse ensuite : c'est le seul autre écart qu'on puisse redresser sans deviner.
--
-- Ce qui ne rejoint toujours aucune entrée devient une ville désactivée. L'école garde la sienne,
-- la contrainte tient, et personne ne se la voit proposer. On n'invente pas de rattachement :
-- « Abidjan » tout court reste « Abidjan » et n'est pas versé d'office dans une commune.
update establishment
set city = nullif(trim(city), '')
where city is not null
  and city is distinct from nullif(trim(city), '');

update establishment e
set city = c.label
from city c
where e.city is not null
  and lower(e.city) = lower(c.label)
  and e.city <> c.label;

-- Les communes d'Abidjan nommées seules — « Bingerville », « Cocody », « Anyama ».
--
-- Ce n'est pas une devinette : le référentiel porte « Abidjan — Bingerville », et une seule entrée
-- s'y termine par ce nom. Le rattachement n'est fait que si cette unicité tient, faute de quoi on
-- retomberait à choisir entre deux villes homonymes — ce que personne ne peut faire à notre place.
-- « Abidjan » tout court, lui, ne désigne aucune commune et reste tel quel.
update establishment e
set city = (select c.label from city c
            where c.region = 'Abidjan' and c.label = 'Abidjan — ' || e.city)
where e.city is not null
  and not exists (select 1 from city c where c.label = e.city)
  and (select count(*) from city c
       where c.region = 'Abidjan' and c.label = 'Abidjan — ' || e.city) = 1;

insert into city (id, created_at, label, active)
select gen_random_uuid(), now(), e.city, false
from (select distinct city from establishment where city is not null) e
where not exists (select 1 from city c where c.label = e.city);

alter table establishment
    add constraint fk_establishment_city
        foreign key (city) references city (label);
