package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentSetupDto;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentSetupDto.Step;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentSetupProgressDto;
import com.ypyit.neoelima.domain.establishment.entity.AcademicYearEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Avancement de la configuration des écoles, pour la console YPYit.
 *
 * <p>Une seule logique pour la fiche d'une école et pour la liste du parc : chaque comptage est
 * groupé par école, si bien que la liste coûte le même nombre de requêtes pour sept écoles ou pour
 * trois cents, et qu'une école affiche le même pourcentage dans la liste et sur sa fiche.
 *
 * <p>Comptages en SQL natif et non par chargement d'entités : dire qu'une école a 400 élèves ne
 * doit pas en hydrater 400. Classes, périodes et frais se lisent sur l'année en cours : une école
 * qui a tout paramétré l'an dernier et rien pour la rentrée n'est pas prête.
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

    /** Fiche d'une école : étapes détaillées et modules complémentaires. */
    public EstablishmentSetupDto setupOf(UUID establishmentId) {
        Optional<AcademicYearEntity> year = this.academicYearContext.current();
        UUID yearId = year.map(AcademicYearEntity::getId).orElse(null);
        Facts facts = this.factsOf(List.of(establishmentId), yearId).get(establishmentId);
        if (Objects.isNull(facts)) {
            throw new NotFoundException("École introuvable");
        }
        List<Step> steps = steps(facts);
        int done = done(steps);
        return EstablishmentSetupDto.builder()
                .steps(steps)
                .modules(this.modules(establishmentId, yearId))
                .done(done)
                .total(steps.size())
                .progress(progress(done, steps.size()))
                .academicYear(year.map(AcademicYearEntity::getLabel).orElse(null))
                .build();
    }

    /** Liste du parc : la seule progression, pour toutes les écoles, en une poignée de requêtes. */
    public List<EstablishmentSetupProgressDto> progressOfAll() {
        UUID yearId = this.academicYearContext.currentId();
        List<UUID> ids = this.ids("select id from establishment");
        List<EstablishmentSetupProgressDto> result = new ArrayList<>();
        this.factsOf(ids, yearId).forEach((id, facts) -> {
            List<Step> steps = steps(facts);
            int done = done(steps);
            result.add(EstablishmentSetupProgressDto.builder()
                    .establishmentId(id)
                    .done(done)
                    .total(steps.size())
                    .progress(progress(done, steps.size()))
                    .build());
        });
        return result;
    }

    /* ------------------------------------------------------------- Faits ---- */

    /** Ce qu'il faut savoir d'une école pour juger de ses étapes. */
    private static final class Facts {
        String city;
        Object logo;
        String plan;
        String planLabel;
        UUID principalId;
        Object principalPassword;
        Object principalLastLogin;
        long phones, emails, levels, classes, periods, fees, scheduled, staff, students, withParent, receipts;
    }

    private Map<UUID, Facts> factsOf(Collection<UUID> ids, UUID yearId) {
        Map<UUID, Facts> facts = new HashMap<>();
        if (ids.isEmpty()) {
            return facts;
        }
        Query schools = this.entityManager.createNativeQuery("""
                select e.id, e.city, e.file_id, e.subscription_plan, e.principal_id,
                       (select p.label from subscription_plan p where p.code = e.subscription_plan),
                       u.password_id, u.last_login
                from establishment e left join users u on u.id = e.principal_id
                where e.id in (:ids)
                """);
        schools.setParameter("ids", ids);
        for (Object row : schools.getResultList()) {
            Object[] r = (Object[]) row;
            Facts f = new Facts();
            f.city = (String) r[1];
            f.logo = r[2];
            f.plan = (String) r[3];
            f.principalId = (UUID) r[4];
            f.planLabel = (String) r[5];
            f.principalPassword = r[6];
            f.principalLastLogin = r[7];
            facts.put((UUID) r[0], f);
        }

        this.grouped("""
                select ec.establishment_id, count(*) from establishment_contacts ec join contact c on c.id = ec.contact_id
                where ec.establishment_id in (:ids) and c.type = 'PHONE_NUMBER' group by 1
                """, ids, null).forEach((id, n) -> with(facts, id).phones = n);
        this.grouped("""
                select ec.establishment_id, count(*) from establishment_contacts ec join contact c on c.id = ec.contact_id
                where ec.establishment_id in (:ids) and c.type = 'EMAIL' group by 1
                """, ids, null).forEach((id, n) -> with(facts, id).emails = n);
        this.grouped("select establishment_id, count(*) from establishment_level_of_studies where establishment_id in (:ids) group by 1",
                ids, null).forEach((id, n) -> with(facts, id).levels = n);
        this.grouped("select establishment_id, count(*) from school_class where establishment_id in (:ids) group by 1",
                ids, null).forEach((id, n) -> with(facts, id).classes = n);
        this.grouped("""
                select sy.establishment_id, count(*) from academic_period p join school_year sy on sy.id = p.school_year_id
                where sy.establishment_id in (:ids) and sy.academic_year_id = :year group by 1
                """, ids, yearId).forEach((id, n) -> with(facts, id).periods = n);
        this.grouped("select establishment_id, count(*) from fee where establishment_id in (:ids) and academic_year_id = :year group by 1",
                ids, yearId).forEach((id, n) -> with(facts, id).fees = n);
        this.grouped("""
                select f.establishment_id, count(distinct f.id) from fee f join fee_schedule s on s.fee_id = f.id
                where f.establishment_id in (:ids) and f.academic_year_id = :year group by 1
                """, ids, yearId).forEach((id, n) -> with(facts, id).scheduled = n);
        this.grouped("select establishment_id, count(*) from staff where establishment_id in (:ids) and active group by 1",
                ids, null).forEach((id, n) -> with(facts, id).staff = n);
        this.grouped("select establishment_id, count(*) from student where establishment_id in (:ids) group by 1",
                ids, null).forEach((id, n) -> with(facts, id).students = n);
        this.grouped("""
                select s.establishment_id, count(distinct s.id) from student s join student_parents sp on sp.student_id = s.id
                where s.establishment_id in (:ids) group by 1
                """, ids, null).forEach((id, n) -> with(facts, id).withParent = n);
        this.grouped("select establishment_id, count(*) from receipt where establishment_id in (:ids) group by 1",
                ids, null).forEach((id, n) -> with(facts, id).receipts = n);
        return facts;
    }

    /** Un comptage qui citerait une école absente (rare course à la suppression) ne crée rien. */
    private static Facts with(Map<UUID, Facts> facts, UUID id) {
        return facts.getOrDefault(id, new Facts());
    }

    /* ------------------------------------------------------------- Étapes ---- */

    private static List<Step> steps(Facts f) {
        List<Step> steps = new ArrayList<>();
        steps.add(identity(f));
        steps.add(principal(f));
        steps.add(subscription(f));
        steps.add(step("levels", "Niveaux enseignés", "Un élève ne s'inscrit que dans un niveau déclaré",
                f.levels, plural(f.levels, "niveau", "niveaux")));
        steps.add(step("classes", "Classes", "Salles, capacités et titulaires",
                f.classes, plural(f.classes, "classe", "classes")));
        steps.add(step("periods", "Périodes de l'année", "Trimestres ou semestres de l'année en cours",
                f.periods, plural(f.periods, "période", "périodes")));
        steps.add(step("fees", "Frais", "Scolarité, inscription, cantine, transport…",
                f.fees, plural(f.fees, "frais défini", "frais définis")));
        steps.add(Step.builder().key("schedules").label("Échéanciers des frais")
                .hint("Le découpage en tranches de chaque frais")
                .count(f.scheduled)
                .done(f.fees > 0 && f.scheduled >= f.fees)
                .detail(f.fees == 0 ? null : f.scheduled + " frais sur " + f.fees + " avec échéancier")
                .build());
        steps.add(step("staff", "Personnel", "Enseignants et administratifs",
                f.staff, plural(f.staff, "membre actif", "membres actifs")));
        steps.add(step("students", "Élèves inscrits", "Saisie une par une ou import CSV",
                f.students, plural(f.students, "élève", "élèves")));
        steps.add(Step.builder().key("parents").label("Parents rattachés")
                .hint("Au moins 80 % des élèves reliés à un parent, pour le paiement en ligne")
                .count(f.withParent)
                .done(f.students > 0 && f.withParent >= Math.ceil(f.students * PARENT_COVERAGE))
                .detail(f.students == 0 ? null
                        : f.withParent + " élève" + (f.withParent > 1 ? "s" : "") + " sur " + f.students
                        + " (" + Math.round(100.0 * f.withParent / f.students) + " %)")
                .build());
        steps.add(step("payments", "Premier encaissement", "Au guichet ou en ligne",
                f.receipts, plural(f.receipts, "reçu émis", "reçus émis")));
        return steps;
    }

    private static Step identity(Facts f) {
        List<String> missing = new ArrayList<>();
        if (Objects.isNull(f.logo)) missing.add("logo");
        if (Objects.isNull(f.city) || f.city.isBlank()) missing.add("ville");
        if (f.phones == 0) missing.add("téléphone");
        if (f.emails == 0) missing.add("e-mail");
        return Step.builder().key("identity").label("Fiche de l'établissement")
                .hint("Logo, ville, téléphone et e-mail de contact")
                .done(missing.isEmpty())
                .count(4 - missing.size())
                .detail(missing.isEmpty() ? "Complète" : "Manque : " + String.join(", ", missing))
                .build();
    }

    private static Step principal(Facts f) {
        Step.StepBuilder step = Step.builder().key("principal").label("Compte de direction")
                .hint("Le directeur a défini son mot de passe");
        if (Objects.isNull(f.principalId)) {
            return step.done(false).detail("Aucun directeur désigné").build();
        }
        boolean activated = Objects.nonNull(f.principalPassword);
        Instant login = f.principalLastLogin instanceof Instant instant ? instant
                : f.principalLastLogin instanceof Timestamp timestamp ? timestamp.toInstant() : null;
        String detail = !activated ? "En attente d'activation"
                : Objects.nonNull(login) ? "Activé · dernière connexion le " + DAY.format(login.atZone(ABIDJAN))
                : "Activé · jamais connecté";
        return step.done(activated).count(activated ? 1 : 0).detail(detail).build();
    }

    private static Step subscription(Facts f) {
        boolean done = Objects.nonNull(f.plan);
        return Step.builder().key("subscription").label("Abonnement Nelima")
                .hint("La formule qui fixe le nombre d'élèves inclus")
                .done(done).count(done ? 1 : 0)
                .detail(done ? Objects.requireNonNullElse(f.planLabel, f.plan) : "Formule à définir")
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

    private static int done(List<Step> steps) {
        return (int) steps.stream().filter(Step::isDone).count();
    }

    private static int progress(int done, int total) {
        return Math.round(100f * done / total);
    }

    private static Step step(String key, String label, String hint, long count, String detail) {
        return Step.builder().key(key).label(label).hint(hint).count(count).done(count > 0)
                .detail(count > 0 ? detail : null).build();
    }

    private static String plural(long n, String one, String many) {
        return n + " " + (n > 1 ? many : one);
    }

    private List<UUID> ids(String sql) {
        return this.entityManager.createNativeQuery(sql).getResultList().stream()
                .map(UUID.class::cast).toList();
    }

    /**
     * Comptage groupé par école. Sans année déclarée sur la plateforme, une requête qui en dépend
     * ne compte rien : l'étape reste à faire.
     */
    private Map<UUID, Long> grouped(String sql, Collection<UUID> ids, UUID yearId) {
        Map<UUID, Long> counts = new HashMap<>();
        if (sql.contains(":year") && Objects.isNull(yearId)) {
            return counts;
        }
        Query query = this.entityManager.createNativeQuery(sql);
        query.setParameter("ids", ids);
        if (sql.contains(":year")) {
            query.setParameter("year", yearId);
        }
        for (Object row : query.getResultList()) {
            Object[] r = (Object[]) row;
            counts.put((UUID) r[0], ((Number) r[1]).longValue());
        }
        return counts;
    }

    /** Comptage d'une seule école, pour les modules de la fiche. */
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
}
