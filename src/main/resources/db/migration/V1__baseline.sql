--
-- Nelima — schéma initial (baseline).
--
-- Généré depuis les entités JPA sur une base vierge, puis figé ici. À partir de
-- cette migration le schéma est piloté exclusivement par Flyway : `ddl-auto` est
-- en `validate` et toute évolution d'entité doit s'accompagner d'un V2__, V3__...
--

--
--

--
-- Name: address; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.address (
    latitude double precision,
    longitude double precision,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    created_by character varying(255),
    modified_by character varying(255),
    name character varying(255)
);

--
-- Name: contact; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contact (
    is_primary boolean NOT NULL,
    whats_app boolean NOT NULL,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    created_by character varying(255),
    modified_by character varying(255),
    type character varying(255),
    value character varying(255),
    CONSTRAINT contact_type_check CHECK (((type)::text = ANY ((ARRAY['EMAIL'::character varying, 'PHONE_NUMBER'::character varying])::text[])))
);

--
-- Name: establishment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.establishment (
    active boolean NOT NULL,
    is_primary boolean NOT NULL,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    address_id uuid,
    file_id uuid,
    id uuid NOT NULL,
    parent_id uuid,
    principal_id uuid,
    bucket_name character varying(255),
    created_by character varying(255),
    modified_by character varying(255),
    name character varying(255),
    web_site character varying(255)
);

--
-- Name: establishment_contacts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.establishment_contacts (
    contact_id uuid NOT NULL,
    establishment_id uuid NOT NULL
);

--
-- Name: establishment_level_of_studies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.establishment_level_of_studies (
    establishment_id uuid NOT NULL,
    level_of_study_id uuid NOT NULL
);

--
-- Name: fee; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fee (
    academical boolean NOT NULL,
    optional boolean NOT NULL,
    price numeric(38,2),
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    establishment_id uuid,
    id uuid NOT NULL,
    created_by character varying(255),
    modified_by character varying(255),
    name character varying(255)
);

--
-- Name: fee_level_of_studies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fee_level_of_studies (
    fee_id uuid NOT NULL,
    level_of_study_id uuid NOT NULL
);

--
-- Name: file_media; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.file_media (
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    created_by character varying(255),
    link character varying(255),
    modified_by character varying(255),
    name character varying(255)
);

--
-- Name: global_parameter; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.global_parameter (
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    code character varying(255),
    created_by character varying(255),
    modified_by character varying(255),
    name character varying(255),
    value character varying(255)
);

--
-- Name: installment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.installment (
    amount numeric(38,2),
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    payment_id uuid,
    student_fee_id uuid,
    created_by character varying(255),
    modified_by character varying(255)
);

--
-- Name: level_of_study; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.level_of_study (
    "position" integer NOT NULL,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    name_id uuid,
    code character varying(255),
    created_by character varying(255),
    modified_by character varying(255),
    next character varying(255),
    previous character varying(255)
);

--
-- Name: password_value; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.password_value (
    expired boolean NOT NULL,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    created_by character varying(255),
    modified_by character varying(255),
    value character varying(255)
);

--
-- Name: permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permission (
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    name_id uuid,
    code character varying(255),
    created_by character varying(255),
    modified_by character varying(255)
);

--
-- Name: role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role (
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    name_id uuid,
    code character varying(255),
    created_by character varying(255),
    modified_by character varying(255)
);

--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permissions (
    permission_id uuid NOT NULL,
    role_id uuid NOT NULL
);

--
-- Name: student; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student (
    birth_day date,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    establishment_id uuid,
    id uuid NOT NULL,
    level_of_study_id uuid,
    created_by character varying(255),
    first_name character varying(255),
    last_name character varying(255),
    modified_by character varying(255),
    place_of_birth character varying(255),
    registration_number character varying(255)
);

--
-- Name: student_fee; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_fee (
    paid boolean NOT NULL,
    created_at timestamp(6) with time zone,
    deadline timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    fee_id uuid,
    id uuid NOT NULL,
    student_id uuid,
    created_by character varying(255),
    modified_by character varying(255),
    name character varying(255)
);

--
-- Name: student_parents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_parents (
    student_id uuid NOT NULL,
    user_id uuid NOT NULL
);

--
-- Name: translate; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.translate (
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    created_by character varying(255),
    en text,
    fr text,
    modified_by character varying(255)
);

--
-- Name: user_contacts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_contacts (
    contact_id uuid NOT NULL,
    user_id uuid NOT NULL
);

--
-- Name: user_session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_session (
    created_at timestamp(6) with time zone,
    session_end_time timestamp(6) without time zone,
    session_start_time timestamp(6) without time zone,
    updated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    user_id uuid,
    created_by character varying(255),
    modified_by character varying(255)
);

--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    enabled boolean NOT NULL,
    locked boolean NOT NULL,
    created_at timestamp(6) with time zone,
    last_login timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    establishment_id uuid,
    id uuid NOT NULL,
    password_id uuid,
    role_id uuid,
    user_type character varying(31) NOT NULL,
    created_by character varying(255),
    first_name character varying(255),
    last_name character varying(255),
    modified_by character varying(255)
);

--
-- Name: address address_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.address
    ADD CONSTRAINT address_pkey PRIMARY KEY (id);

--
-- Name: contact contact_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contact
    ADD CONSTRAINT contact_pkey PRIMARY KEY (id);

--
-- Name: establishment establishment_address_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.establishment
    ADD CONSTRAINT establishment_address_id_key UNIQUE (address_id);

--
-- Name: establishment_contacts establishment_contacts_contact_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.establishment_contacts
    ADD CONSTRAINT establishment_contacts_contact_id_key UNIQUE (contact_id);

--
-- Name: establishment_contacts establishment_contacts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.establishment_contacts
    ADD CONSTRAINT establishment_contacts_pkey PRIMARY KEY (contact_id, establishment_id);

--
-- Name: establishment establishment_file_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.establishment
    ADD CONSTRAINT establishment_file_id_key UNIQUE (file_id);

--
-- Name: establishment_level_of_studies establishment_level_of_studies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.establishment_level_of_studies
    ADD CONSTRAINT establishment_level_of_studies_pkey PRIMARY KEY (establishment_id, level_of_study_id);

--
-- Name: establishment establishment_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.establishment
    ADD CONSTRAINT establishment_name_key UNIQUE (name);

--
-- Name: establishment establishment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.establishment
    ADD CONSTRAINT establishment_pkey PRIMARY KEY (id);

--
-- Name: fee_level_of_studies fee_level_of_studies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fee_level_of_studies
    ADD CONSTRAINT fee_level_of_studies_pkey PRIMARY KEY (fee_id, level_of_study_id);

--
-- Name: fee fee_name_establishment_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fee
    ADD CONSTRAINT fee_name_establishment_id_key UNIQUE (name, establishment_id);

--
-- Name: fee fee_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fee
    ADD CONSTRAINT fee_pkey PRIMARY KEY (id);

--
-- Name: file_media file_media_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.file_media
    ADD CONSTRAINT file_media_pkey PRIMARY KEY (id);

--
-- Name: global_parameter global_parameter_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.global_parameter
    ADD CONSTRAINT global_parameter_code_key UNIQUE (code);

--
-- Name: global_parameter global_parameter_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.global_parameter
    ADD CONSTRAINT global_parameter_pkey PRIMARY KEY (id);

--
-- Name: installment installment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.installment
    ADD CONSTRAINT installment_pkey PRIMARY KEY (id);

--
-- Name: level_of_study level_of_study_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.level_of_study
    ADD CONSTRAINT level_of_study_code_key UNIQUE (code);

--
-- Name: level_of_study level_of_study_name_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.level_of_study
    ADD CONSTRAINT level_of_study_name_id_key UNIQUE (name_id);

--
-- Name: level_of_study level_of_study_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.level_of_study
    ADD CONSTRAINT level_of_study_pkey PRIMARY KEY (id);

--
-- Name: password_value password_value_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_value
    ADD CONSTRAINT password_value_pkey PRIMARY KEY (id);

--
-- Name: permission permission_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission
    ADD CONSTRAINT permission_code_key UNIQUE (code);

--
-- Name: permission permission_name_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission
    ADD CONSTRAINT permission_name_id_key UNIQUE (name_id);

--
-- Name: permission permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission
    ADD CONSTRAINT permission_pkey PRIMARY KEY (id);

--
-- Name: role role_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role
    ADD CONSTRAINT role_code_key UNIQUE (code);

--
-- Name: role role_name_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role
    ADD CONSTRAINT role_name_id_key UNIQUE (name_id);

--
-- Name: role_permissions role_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (permission_id, role_id);

--
-- Name: role role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role
    ADD CONSTRAINT role_pkey PRIMARY KEY (id);

--
-- Name: student_fee student_fee_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_fee
    ADD CONSTRAINT student_fee_pkey PRIMARY KEY (id);

--
-- Name: student_parents student_parents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_parents
    ADD CONSTRAINT student_parents_pkey PRIMARY KEY (student_id, user_id);

--
-- Name: student student_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student
    ADD CONSTRAINT student_pkey PRIMARY KEY (id);

--
-- Name: student student_registration_number_establishment_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student
    ADD CONSTRAINT student_registration_number_establishment_id_key UNIQUE (registration_number, establishment_id);

--
-- Name: translate translate_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.translate
    ADD CONSTRAINT translate_pkey PRIMARY KEY (id);

--
-- Name: user_contacts user_contacts_contact_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_contacts
    ADD CONSTRAINT user_contacts_contact_id_key UNIQUE (contact_id);

--
-- Name: user_contacts user_contacts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_contacts
    ADD CONSTRAINT user_contacts_pkey PRIMARY KEY (contact_id, user_id);

--
-- Name: user_session user_session_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_session
    ADD CONSTRAINT user_session_pkey PRIMARY KEY (id);

--
-- Name: users users_password_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_password_id_key UNIQUE (password_id);

--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

--
-- Name: student fk174q3o704iisfekl37did3eo7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student
    ADD CONSTRAINT fk174q3o704iisfekl37did3eo7 FOREIGN KEY (level_of_study_id) REFERENCES public.level_of_study(id);

--
-- Name: installment fk1ncei8yuyvladgm7rrt90xvf3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.installment
    ADD CONSTRAINT fk1ncei8yuyvladgm7rrt90xvf3 FOREIGN KEY (student_fee_id) REFERENCES public.student_fee(id);

--
-- Name: users fk4qu1gr772nnf6ve5af002rwya; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk4qu1gr772nnf6ve5af002rwya FOREIGN KEY (role_id) REFERENCES public.role(id);

--
-- Name: user_contacts fk6l84v0sjnmmnpaqlpjxjdbbqh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_contacts
    ADD CONSTRAINT fk6l84v0sjnmmnpaqlpjxjdbbqh FOREIGN KEY (contact_id) REFERENCES public.contact(id);

--
-- Name: role fk80iekdo9276y1kcx8l5laamw9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role
    ADD CONSTRAINT fk80iekdo9276y1kcx8l5laamw9 FOREIGN KEY (name_id) REFERENCES public.translate(id);

--
-- Name: permission fk8ac5buixig8u4f7fn0olyefvo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission
    ADD CONSTRAINT fk8ac5buixig8u4f7fn0olyefvo FOREIGN KEY (name_id) REFERENCES public.translate(id);

--
-- Name: fee fk9refdx7wkqu5o5r8pxky3735d; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fee
    ADD CONSTRAINT fk9refdx7wkqu5o5r8pxky3735d FOREIGN KEY (establishment_id) REFERENCES public.establishment(id);

--
-- Name: establishment_contacts fk9vjpjhwa21yko3fqrgeriglkb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.establishment_contacts
    ADD CONSTRAINT fk9vjpjhwa21yko3fqrgeriglkb FOREIGN KEY (contact_id) REFERENCES public.contact(id);

--
-- Name: establishment_contacts fk9xhje76xm08c0k85eccxt21fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.establishment_contacts
    ADD CONSTRAINT fk9xhje76xm08c0k85eccxt21fk FOREIGN KEY (establishment_id) REFERENCES public.establishment(id);

--
-- Name: fee_level_of_studies fkbn77elkf2keidw8crilgbvyc0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fee_level_of_studies
    ADD CONSTRAINT fkbn77elkf2keidw8crilgbvyc0 FOREIGN KEY (fee_id) REFERENCES public.fee(id);

--
-- Name: establishment fkdnwoscwy9ak9kaqjvmi9guj6q; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.establishment
    ADD CONSTRAINT fkdnwoscwy9ak9kaqjvmi9guj6q FOREIGN KEY (principal_id) REFERENCES public.users(id);

--
-- Name: establishment fkefqh5pu0leyiduurk01bqtnrs; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.establishment
    ADD CONSTRAINT fkefqh5pu0leyiduurk01bqtnrs FOREIGN KEY (file_id) REFERENCES public.file_media(id);

--
-- Name: student_fee fkesudo1i3xsjulovy4enioa0kc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_fee
    ADD CONSTRAINT fkesudo1i3xsjulovy4enioa0kc FOREIGN KEY (student_id) REFERENCES public.student(id);

--
-- Name: role_permissions fkh0v7u4w7mttcu81o8wegayr8e; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fkh0v7u4w7mttcu81o8wegayr8e FOREIGN KEY (permission_id) REFERENCES public.permission(id);

--
-- Name: student_parents fkhodep2idsdr5o7j9meqlscjqk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_parents
    ADD CONSTRAINT fkhodep2idsdr5o7j9meqlscjqk FOREIGN KEY (user_id) REFERENCES public.users(id);

--
-- Name: establishment_level_of_studies fkij6thfsw17h5ebkvltn865sgr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.establishment_level_of_studies
    ADD CONSTRAINT fkij6thfsw17h5ebkvltn865sgr FOREIGN KEY (level_of_study_id) REFERENCES public.level_of_study(id);

--
-- Name: student_fee fkirnsgs3ymn7hmb1i05lgyl5d1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_fee
    ADD CONSTRAINT fkirnsgs3ymn7hmb1i05lgyl5d1 FOREIGN KEY (fee_id) REFERENCES public.fee(id);

--
-- Name: users fkisni84nyv21y1jqsr0qjpqure; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fkisni84nyv21y1jqsr0qjpqure FOREIGN KEY (establishment_id) REFERENCES public.establishment(id);

--
-- Name: establishment_level_of_studies fkk0hwfc3mcxrccfvsi472g3yed; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.establishment_level_of_studies
    ADD CONSTRAINT fkk0hwfc3mcxrccfvsi472g3yed FOREIGN KEY (establishment_id) REFERENCES public.establishment(id);

--
-- Name: establishment fkk4hivjjucahg0xc6omo1rpl3v; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.establishment
    ADD CONSTRAINT fkk4hivjjucahg0xc6omo1rpl3v FOREIGN KEY (address_id) REFERENCES public.address(id);

--
-- Name: establishment fkked726or6itb6h7gptcunwgfc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.establishment
    ADD CONSTRAINT fkked726or6itb6h7gptcunwgfc FOREIGN KEY (parent_id) REFERENCES public.establishment(id);

--
-- Name: users fkkqacck80t44qfgy63dg19s72b; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fkkqacck80t44qfgy63dg19s72b FOREIGN KEY (password_id) REFERENCES public.password_value(id);

--
-- Name: role_permissions fklodb7xh4a2xjv39gc3lsop95n; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fklodb7xh4a2xjv39gc3lsop95n FOREIGN KEY (role_id) REFERENCES public.role(id);

--
-- Name: fee_level_of_studies fkmuw3e23wglkj0xqbnufbjt8in; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fee_level_of_studies
    ADD CONSTRAINT fkmuw3e23wglkj0xqbnufbjt8in FOREIGN KEY (level_of_study_id) REFERENCES public.level_of_study(id);

--
-- Name: level_of_study fkobel3m5j8nrsu98ur0md6wxbv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.level_of_study
    ADD CONSTRAINT fkobel3m5j8nrsu98ur0md6wxbv FOREIGN KEY (name_id) REFERENCES public.translate(id);

--
-- Name: student_parents fkqfkpyl0i2t5m1hhgtqgkusi0a; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_parents
    ADD CONSTRAINT fkqfkpyl0i2t5m1hhgtqgkusi0a FOREIGN KEY (student_id) REFERENCES public.student(id);

--
-- Name: user_contacts fkqgbpf3rh5b6i7npvr2rf776rd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_contacts
    ADD CONSTRAINT fkqgbpf3rh5b6i7npvr2rf776rd FOREIGN KEY (user_id) REFERENCES public.users(id);

--
-- Name: student fkqymaypqqdnmrd47hdbhg5dc7b; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student
    ADD CONSTRAINT fkqymaypqqdnmrd47hdbhg5dc7b FOREIGN KEY (establishment_id) REFERENCES public.establishment(id);

--
--
