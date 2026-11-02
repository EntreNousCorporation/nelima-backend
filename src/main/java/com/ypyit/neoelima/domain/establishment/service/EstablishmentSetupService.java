package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentSetupDto;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentSetupDto.Step;
import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Avancement de la configuration d'une école, pour la console YPYit.
 *
 * <p>Comptages en SQL natif et non par chargement d'entités : la fiche s'ouvre d'un clic depuis la
 * liste du parc, elle ne doit pas hydrater 400 élèves pour en dire le nombre.
 *
 * <p>Classes, périodes et frais se lisent sur l'année en cours : une école qui a tout paramétré
 * l'an dernier et rien pour la rentrée n'est pas prête.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EstablishmentSetupService {

    /** Part d'élèves rattachés à au moins un parent pour que l'étape soit tenue pour faite. */
    static final double PARENT_COVERAGE = 0.8;

    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    private final EntityManager entityManager;
    private final AcademicYearContext academicYearContext;

    public EstablishmentSetupDto setupOf(UUID establishmentId) {
        Object[] school = this.single("""
                select e.name, e.city, e.file_id, e.subscription_plan, e.principal_id,
                       (select p.label from subscription_plan p where p.code = e.subscription_plan)
                from establishment e where e.id = :id
                """, Map.of("id", establishmentId))
                .orElseThrow(() -> new NotFoundException("École introuvable"));
        Optional<AcademicYearEntity> year = this.academicYearContext.current();
        UUID yearId = year.map(AcademicYearEntity::getId).orElse(null);

        List<Step> steps = new ArrayList<>();
        steps.add(this.identity(establishmentId, school));
        steps.add(this.principal((UUID) school[4]));
        steps.add(this.subscription((String) school[3], (String) school[5]));

        long levels = this.count("select count(*) from establishment_level_of_studies where establishment_id = :id",
                establishmentId, null);
        steps.add(step("levels", "Niveaux enseignés", "Un élève ne s'inscrit que dans un niveau déclaré",
                levels, plural(levels, "niveau", "niveaux")));

        long classes = this.count("select count(*) from school_class where establishment_id = :id", establishmentId, null);
        steps.add(step("classes", "Classes", "Salles, capacités et titulaires",
                classes, plural(classes, "classe", "classes")));

        long periods = this.count("""
                select count(*) from academic_period p join school_year sy on sy.id = p.school_year_id
                where sy.establishment_id = :id and sy.academic_year_id = :year
                """, establishmentId, yearId);
        steps.add(step("periods", "Périodes de l'année", "Trimestres ou semestres de l'année en cours",
                periods, plural(periods, "période", "périodes")));

        long fees = this.count("select count(*) from fee where establishment_id = :id and academic_year_id = :year",
                establishmentId, yearId);
        steps.add(step("fees", "Frais", "Scolarité, inscription, cantine, transport…",
                fees, plural(fees, "frais défini", "frais définis")));

        long scheduled = this.count("""
                select count(distinct f.id) from fee f join fee_schedule s on s.fee_id = f.id
                where f.establishment_id = :id and f.academic_year_id = :year
                """, establishmentId, yearId);
        steps.add(Step.builder().key("schedules").label("Échéanciers des frais")
                .hint("Le découpage en tranches de chaque frais")
                .count(scheduled)
                .done(fees > 0 && scheduled >= fees)
                .detail(fees == 0 ? null : scheduled + " frais sur " + fees + " avec échéancier")
                .build());

        long staff = this.count("select count(*) from staff where establishment_id = :id and active", establishmentId, null);
        steps.add(step("staff", "Personnel", "Enseignants et administratifs",
                staff, plural(staff, "membre actif", "membres actifs")));

        long students = this.count("select count(*) from student where establishment_id = :id", establishmentId, null);
        steps.add(step("students", "Élèves inscrits", "Saisie une par une ou import CSV",
                students, plural(students, "élève", "élèves")));

        long withParent = this.count("""
                select count(distinct s.id) from student s join student_parents sp on sp.student_id = s.id
                where s.establishment_id = :id
                """, establishmentId, null);
        steps.add(Step.builder().key("parents").label("Parents rattachés")
                .hint("Au moins 80 % des élèves reliés à un parent, pour le paiement en ligne")
                .count(withParent)
                .done(students > 0 && withParent >= Math.ceil(students * PARENT_COVERAGE))
                .detail(students == 0 ? null
                        : withParent + " élève" + (withParent > 1 ? "s" : "") + " sur " + students
                        + " (" + Math.round(100.0 * withParent / students) + " %)")
                .build());

        long receipts = this.count("select count(*) from receipt where establishment_id = :id", establishmentId, null);
        steps.add(step("payments", "Premier encaissement", "Au guichet ou en ligne",
                receipts, plural(receipts, "reçu émis", "reçus émis")));

        int done = (int) steps.stream().filter(Step::isDone).count();
        return EstablishmentSetupDto.builder()
                .steps(steps)
                .modules(this.modules(establishmentId, yearId))
                .done(done)
                .total(steps.size())
                .progress(Math.round(100f * done / steps.size()))
                .academicYear(year.map(AcademicYearEntity::getLabel).orElse(null))
                .build();
    }

    /* ------------------------------------------------------------- Étapes ---- */

    private Step identity(UUID establishmentId, Object[] school) {
        List<String> missing = new ArrayList<>();
        if (Objects.isNull(school[2])) missing.add("logo");
        if (isBlank(school[1])) missing.add("ville");
        long phones = this.contacts(establishmentId, "PHONE_NUMBER");
        long emails = this.contacts(establishmentId, "EMAIL");
        if (phones == 0) missing.add("téléphone");
        if (emails == 0) missing.add("e-mail");
        return Step.builder().key("identity").label("Fiche de l'établissement")
                .hint("Logo, ville, téléphone et e-mail de contact")
                .done(missing.isEmpty())
                .count(4 - missing.size())
                .detail(missing.isEmpty() ? "Complète" : "Manque : " + String.join(", ", missing))
                .build();
    }

    private Step principal(UUID principalId) {
        Step.StepBuilder step = Step.builder().key("principal").label("Compte de direction")
                .hint("Le directeur a défini son mot de passe");
        if (Objects.isNull(principalId)) {
            return step.done(false).detail("Aucun directeur désigné").build();
        }
        Object[] user = this.single("select u.password_id, u.last_login from users u where u.id = :id",
                Map.of("id", principalId)).orElse(new Object[]{null, null});
        boolean activated = Objects.nonNull(user[0]);
        String detail;
        if (!activated) {
            detail = "En attente d'activation";
        } else if (user[1] instanceof Instant login) {
            detail = "Activé · dernière connexion le " + DAY.format(login.atZone(ABIDJAN));
        } else if (user[1] instanceof java.sql.Timestamp login) {
            detail = "Activé · dernière connexion le " + DAY.format(login.toInstant().atZone(ABIDJAN));
        } else {
            detail = "Activé · jamais connecté";
        }
        return step.done(activated).count(activated ? 1 : 0).detail(detail).build();
    }

    private Step subscription(String plan, String label) {
        boolean done = Objects.nonNull(plan);
        return Step.builder().key("subscription").label("Abonnement Nelima")
                .hint("La formule qui fixe le nombre d'élèves inclus")
                .done(done).count(done ? 1 : 0)
                .detail(done ? Objects.requireNonNullElse(label, plan) : "Formule à définir")
                .build();
    }

    private List<Step> modules(UUID id, UUID yearId) {
        List<Step> modules = new ArrayList<>();
        long activities = this.count("select count(*) from activity where establishment_id = :id and academic_year_id = :year", id, yearId);
        modules.add(step("activities", "Activités extrascolaires", "Clubs et ateliers proposés aux familles",
                activities, plural(activities, "activité", "activités")));
        long formulas = this.count("select count(*) from canteen_formula where establishment_id = :id and active", id, null);
        long menus = this.count("select count(*) from canteen_menu_day where establishment_id = :id", id, null);
        modules.add(Step.builder().key("canteen").label("Cantine").hint("Formules et menus de la semaine")
                .count(formulas).done(formulas > 0)
                .detail(formulas == 0 ? null : plural(formulas, "formule", "formules") + " · " + plural(menus, "menu", "menus"))
                .build());
        long lines = this.count("select count(*) from transport_line where establishment_id = :id and active", id, null);
        long vehicles = this.count("select count(*) from transport_vehicle where establishment_id = :id and active", id, null);
        long drivers = this.count("select count(*) from transport_driver where establishment_id = :id and active", id, null);
        modules.add(Step.builder().key("transport").label("Transport scolaire").hint("Lignes, véhicules et chauffeurs")
                .count(lines).done(lines > 0)
                .detail(lines == 0 ? null : plural(lines, "ligne", "lignes") + " · " + plural(vehicles, "véhicule", "véhicules")
                        + " · " + plural(drivers, "chauffeur", "chauffeurs"))
                .build());
        long timetable = this.count("""
                select count(*) from timetable_slot t join school_class c on c.id = t.school_class_id
                where c.establishment_id = :id and t.academic_year_id = :year
                """, id, yearId);
        modules.add(step("timetable", "Emploi du temps", "Créneaux par classe",
                timetable, plural(timetable, "créneau", "créneaux")));
        long events = this.count("select count(*) from school_event where establishment_id = :id and academic_year_id = :year", id, yearId);
        modules.add(step("calendar", "Calendrier", "Événements et échéances de l'année",
                events, plural(events, "événement", "événements")));
        long campaigns = this.count("select count(*) from reminder_campaign where establishment_id = :id", id, null);
        modules.add(step("reminders", "Relances", "Campagnes de rappel aux familles",
                campaigns, plural(campaigns, "campagne", "campagnes")));
        long roles = this.count("select count(*) from establishment_role where establishment_id = :id", id, null);
        modules.add(step("roles", "Habilitations", "Rôles propres à l'école",
                roles, plural(roles, "rôle personnalisé", "rôles personnalisés")));
        long notifications = this.count("select count(*) from notification_preference where establishment_id = :id", id, null);
        modules.add(step("notifications", "Notifications", "Préférences d'envoi de l'école",
                notifications, plural(notifications, "règle", "règles")));
        return modules;
    }

    /* ------------------------------------------------------------- Outils ---- */

    private static Step step(String key, String label, String hint, long count, String detail) {
        return Step.builder().key(key).label(label).hint(hint).count(count).done(count > 0)
                .detail(count > 0 ? detail : null).build();
    }

    private static String plural(long n, String one, String many) {
        return n + " " + (n > 1 ? many : one);
    }

    private static boolean isBlank(Object value) {
        return Objects.isNull(value) || value.toString().isBlank();
    }

    private long contacts(UUID establishmentId, String type) {
        Query query = this.entityManager.createNativeQuery("""
                select count(*) from establishment_contacts ec join contact c on c.id = ec.contact_id
                where ec.establishment_id = :id and c.type = :type
                """);
        query.setParameter("id", establishmentId);
        query.setParameter("type", type);
        return ((Number) query.getSingleResult()).longValue();
    }

    /**
     * Comptage scopé à l'école, et à l'année quand la requête la cite. Sans année déclarée sur la
     * plateforme, une requête qui en dépend compte zéro : l'étape reste à faire.
     */
    private long count(String sql, UUID establishmentId, UUID yearId) {
        if (sql.contains(":year") && Objects.isNull(yearId)) {
            return 0;
        }
        Query query = this.entityManager.createNativeQuery(sql);
        query.setParameter("id", establishmentId);
        if (sql.contains(":year")) {
            query.setParameter("year", yearId);
        }
        return ((Number) query.getSingleResult()).longValue();
    }

    private Optional<Object[]> single(String sql, Map<String, Object> parameters) {
        Query query = this.entityManager.createNativeQuery(sql);
        parameters.forEach(query::setParameter);
        List<?> rows = query.getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of((Object[]) rows.get(0));
    }
}
