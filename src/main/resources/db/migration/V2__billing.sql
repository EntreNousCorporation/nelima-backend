--
-- Facturation : tranches de frais, tentatives de paiement et reçus.
--
-- Généré depuis les entités JPA par différence avec le schéma V1, puis relu.
--

-- Tranche telle que l'école la définit sur un frais. C'est un gabarit, décliné
-- en `installment` pour chaque élève concerné.
CREATE TABLE public.fee_schedule (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone,
    created_by character varying(255),
    modified_by character varying(255),
    updated_at timestamp(6) with time zone,
    amount numeric(38,2) NOT NULL,
    due_date date,
    label character varying(255),
    "position" integer NOT NULL,
    fee_id uuid
);

ALTER TABLE ONLY public.fee_schedule
    ADD CONSTRAINT fee_schedule_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.fee_schedule
    ADD CONSTRAINT uk_fee_schedule_fee_position UNIQUE (fee_id, "position");

ALTER TABLE ONLY public.fee_schedule
    ADD CONSTRAINT fk_fee_schedule_fee FOREIGN KEY (fee_id) REFERENCES public.fee(id);


-- `installment` devient la dette datée et suivie d'un élève, et non plus un
-- simple montant. Les lignes existantes sont réputées en attente.
ALTER TABLE public.installment ADD COLUMN label character varying(255);
ALTER TABLE public.installment ADD COLUMN due_date date;
ALTER TABLE public.installment ADD COLUMN paid_at timestamp(6) with time zone;
ALTER TABLE public.installment ADD COLUMN status character varying(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE public.installment ADD COLUMN fee_schedule_id uuid;

ALTER TABLE public.installment
    ADD CONSTRAINT installment_status_check
    CHECK (status IN ('PENDING', 'PAID', 'CANCELLED'));

ALTER TABLE ONLY public.installment
    ADD CONSTRAINT fk_installment_fee_schedule FOREIGN KEY (fee_schedule_id) REFERENCES public.fee_schedule(id);


-- Tentative de règlement d'une tranche.
CREATE TABLE public.payment_intent (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone,
    created_by character varying(255),
    modified_by character varying(255),
    updated_at timestamp(6) with time zone,
    amount_commission numeric(38,2) NOT NULL,
    amount_school numeric(38,2) NOT NULL,
    channel character varying(20) NOT NULL,
    checkout_url character varying(2048),
    currency character varying(3) NOT NULL,
    internal_reference character varying(64),
    provider_type character varying(255),
    settled_at timestamp(6) with time zone,
    status character varying(20) NOT NULL,
    installment_id uuid NOT NULL,
    payer_user_id uuid,
    CONSTRAINT payment_intent_channel_check
        CHECK (channel IN ('ONLINE', 'CASH', 'CHECK', 'BANK_TRANSFER')),
    CONSTRAINT payment_intent_status_check
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED'))
);

ALTER TABLE ONLY public.payment_intent
    ADD CONSTRAINT payment_intent_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.payment_intent
    ADD CONSTRAINT fk_payment_intent_installment FOREIGN KEY (installment_id) REFERENCES public.installment(id);

ALTER TABLE ONLY public.payment_intent
    ADD CONSTRAINT fk_payment_intent_payer FOREIGN KEY (payer_user_id) REFERENCES public.users(id);

-- C'est cet index qui rend le traitement du webhook idempotent : les agrégateurs
-- rejouent leurs notifications, et un même encaissement ne doit produire qu'un
-- seul reçu. Index partiel car les encaissements hors ligne n'ont pas de
-- référence agrégateur.
CREATE UNIQUE INDEX uk_payment_intent_internal_reference
    ON public.payment_intent (internal_reference)
    WHERE internal_reference IS NOT NULL;

-- Sert la réconciliation des tentatives restées en attente.
CREATE INDEX idx_payment_intent_status_created_at
    ON public.payment_intent (status, created_at);


-- Compteur de reçus, une ligne par établissement, verrouillée en SELECT ... FOR
-- UPDATE le temps d'incrémenter. Une séquence PostgreSQL laisserait des trous en
-- cas de rollback, ce qu'une numérotation comptable ne tolère pas.
CREATE TABLE public.receipt_counter (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone,
    created_by character varying(255),
    modified_by character varying(255),
    updated_at timestamp(6) with time zone,
    last_sequence bigint NOT NULL,
    establishment_id uuid NOT NULL
);

ALTER TABLE ONLY public.receipt_counter
    ADD CONSTRAINT receipt_counter_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.receipt_counter
    ADD CONSTRAINT uk_receipt_counter_establishment UNIQUE (establishment_id);

ALTER TABLE ONLY public.receipt_counter
    ADD CONSTRAINT fk_receipt_counter_establishment FOREIGN KEY (establishment_id) REFERENCES public.establishment(id);


-- Reçu émis pour tout encaissement, en ligne comme au guichet. Les libellés
-- d'élève et de payeur sont recopiés à l'émission : une pièce comptable est figée.
CREATE TABLE public.receipt (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone,
    created_by character varying(255),
    modified_by character varying(255),
    updated_at timestamp(6) with time zone,
    amount numeric(38,2) NOT NULL,
    issued_at timestamp(6) with time zone NOT NULL,
    number character varying(64) NOT NULL,
    payer_label character varying(255),
    sequence_number bigint NOT NULL,
    student_label character varying(255),
    student_registration_number character varying(255),
    establishment_id uuid NOT NULL,
    payment_intent_id uuid NOT NULL
);

ALTER TABLE ONLY public.receipt
    ADD CONSTRAINT receipt_pkey PRIMARY KEY (id);

-- Dernier rempart de la numérotation : même si l'allocation déraille, la base
-- refuse deux reçus portant le même rang dans une école.
ALTER TABLE ONLY public.receipt
    ADD CONSTRAINT uk_receipt_establishment_sequence UNIQUE (establishment_id, sequence_number);

-- Un encaissement ne donne qu'un reçu.
ALTER TABLE ONLY public.receipt
    ADD CONSTRAINT uk_receipt_payment_intent UNIQUE (payment_intent_id);

ALTER TABLE ONLY public.receipt
    ADD CONSTRAINT fk_receipt_establishment FOREIGN KEY (establishment_id) REFERENCES public.establishment(id);

ALTER TABLE ONLY public.receipt
    ADD CONSTRAINT fk_receipt_payment_intent FOREIGN KEY (payment_intent_id) REFERENCES public.payment_intent(id);
