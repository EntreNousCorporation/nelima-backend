package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentOverviewDto;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentOverviewDto.Account;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentOverviewDto.Level;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentOverviewDto.SchoolClass;
import com.ypyit.neoelima.domain.establishment.dto.EstablishmentOverviewDto.StaffMember;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * La composition d'une école pour la page école de la console YPYit.
 *
 * <p>Requêtes natives et groupées, comme {@link EstablishmentSetupService} : une école compte
 * des dizaines de classes et de membres du personnel, les charger entité par entité multipliait
 * les allers-retours pour un écran en lecture seule.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EstablishmentOverviewService {

    private final EntityManager entityManager;

    public EstablishmentOverviewDto overviewOf(UUID establishmentId) {
        Number exists = (Number) this.query("select count(*) from establishment where id = :id", establishmentId)
                .getSingleResult();
        if (exists.longValue() == 0) {
            throw new NotFoundException("École introuvable");
        }
        Number withoutClass = (Number) this.query(
                "select count(*) from student where establishment_id = :id and school_class_id is null", establishmentId)
                .getSingleResult();
        return EstablishmentOverviewDto.builder()
                .accounts(this.accounts(establishmentId))
                .staff(this.staff(establishmentId))
                .levels(this.levels(establishmentId))
                .studentsWithoutClass(withoutClass.longValue())
                .build();
    }

    /**
     * Les comptes du portail : ceux rattachés à l'école, et le directeur même s'il ne l'était pas.
     * Les familles ont leurs propres comptes, sur l'application : elles n'y figurent pas.
     */
    private List<Account> accounts(UUID id) {
        List<Account> accounts = new ArrayList<>();
        for (Object[] r : this.rows("""
                select u.id, u.first_name, u.last_name, t.fr, coalesce(e.principal_id = u.id, false),
                       u.password_id is not null, u.last_login, u.enabled, u.locked, u.group_wide,
                       (select c.value from user_contacts uc join contact c on c.id = uc.contact_id
                        where uc.user_id = u.id and c.type = 'EMAIL' order by c.is_primary desc limit 1),
                       (select c.value from user_contacts uc join contact c on c.id = uc.contact_id
                        where uc.user_id = u.id and c.type = 'PHONE_NUMBER' order by c.is_primary desc limit 1)
                from users u
                join establishment e on e.id = :id
                left join role r on r.id = u.role_id
                left join translate t on t.id = r.name_id
                where (u.establishment_id = :id and u.user_type = 'ESTABLISHMENT_USER') or u.id = e.principal_id
                order by 5 desc, u.last_name, u.first_name
                """, id)) {
            accounts.add(Account.builder()
                    .id((UUID) r[0])
                    .firstName((String) r[1])
                    .lastName((String) r[2])
                    .role((String) r[3])
                    .principal(Boolean.TRUE.equals(r[4]))
                    .activated(Boolean.TRUE.equals(r[5]))
                    .lastLogin(instant(r[6]))
                    .enabled(Boolean.TRUE.equals(r[7]))
                    .locked(Boolean.TRUE.equals(r[8]))
                    .groupWide(Boolean.TRUE.equals(r[9]))
                    .email((String) r[10])
                    .phone((String) r[11])
                    .build());
        }
        return accounts;
    }

    private List<StaffMember> staff(UUID id) {
        List<StaffMember> staff = new ArrayList<>();
        for (Object[] r : this.rows("""
                select s.id, s.first_name, s.last_name, s.role, s.job_title, s.phone, s.email,
                       s.contract_type, s.hired_at, s.active, s.user_id is not null,
                       (select string_agg(sc.name, '|' order by sc.name) from school_class sc where sc.main_teacher_id = s.id)
                from staff s
                where s.establishment_id = :id
                order by s.active desc, s.last_name, s.first_name
                """, id)) {
            staff.add(StaffMember.builder()
                    .id((UUID) r[0])
                    .firstName((String) r[1])
                    .lastName((String) r[2])
                    .role((String) r[3])
                    .jobTitle((String) r[4])
                    .phone((String) r[5])
                    .email((String) r[6])
                    .contractType((String) r[7])
                    .hiredAt(localDate(r[8]))
                    .active(Boolean.TRUE.equals(r[9]))
                    .portalAccess(Boolean.TRUE.equals(r[10]))
                    .mainTeacherOf(Objects.isNull(r[11]) ? List.of() : Arrays.asList(((String) r[11]).split("\\|")))
                    .build());
        }
        return staff;
    }

    /**
     * Les niveaux déclarés, dans l'ordre du cursus, chacun avec ses classes. Une classe dont le
     * niveau n'est plus déclaré n'est pas perdue : elle se range sous « Niveau non déclaré ».
     */
    private List<Level> levels(UUID id) {
        Map<UUID, Level> levels = new LinkedHashMap<>();
        for (Object[] r : this.rows("""
                select l.id, l.code, t.fr, l.cycle,
                       (select count(*) from student s where s.establishment_id = :id and s.level_of_study_id = l.id)
                from establishment_level_of_studies el
                join level_of_study l on l.id = el.level_of_study_id
                left join translate t on t.id = l.name_id
                where el.establishment_id = :id
                order by l.position
                """, id)) {
            levels.put((UUID) r[0], Level.builder()
                    .code((String) r[1])
                    .label(Objects.nonNull(r[2]) ? (String) r[2] : (String) r[1])
                    .cycle((String) r[3])
                    .students(((Number) r[4]).longValue())
                    .classes(new ArrayList<>())
                    .build());
        }
        Level undeclared = null;
        for (Object[] r : this.rows("""
                select sc.id, sc.name, sc.capacity, sc.room,
                       coalesce(nullif(trim(concat(st.first_name, ' ', st.last_name)), ''), sc.main_teacher_name),
                       sc.level_of_study_id,
                       (select count(*) from student s where s.school_class_id = sc.id)
                from school_class sc
                left join staff st on st.id = sc.main_teacher_id
                where sc.establishment_id = :id
                order by sc.name
                """, id)) {
            SchoolClass schoolClass = SchoolClass.builder()
                    .id((UUID) r[0])
                    .name((String) r[1])
                    .capacity(Objects.isNull(r[2]) ? null : ((Number) r[2]).intValue())
                    .room((String) r[3])
                    .mainTeacher((String) r[4])
                    .students(((Number) r[6]).longValue())
                    .build();
            Level level = levels.get((UUID) r[5]);
            if (Objects.isNull(level)) {
                if (Objects.isNull(undeclared)) {
                    undeclared = Level.builder().label("Niveau non déclaré").classes(new ArrayList<>()).build();
                }
                level = undeclared;
            }
            level.getClasses().add(schoolClass);
        }
        List<Level> ordered = new ArrayList<>(levels.values());
        if (Objects.nonNull(undeclared)) {
            ordered.add(undeclared);
        }
        return ordered;
    }

    private Query query(String sql, UUID id) {
        Query query = this.entityManager.createNativeQuery(sql);
        query.setParameter("id", id);
        return query;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String sql, UUID id) {
        return (List<Object[]>) this.query(sql, id).getResultList();
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offset) {
            return offset.toInstant();
        }
        return value instanceof Timestamp timestamp ? timestamp.toInstant() : null;
    }

    private static LocalDate localDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        return value instanceof Date date ? date.toLocalDate() : null;
    }
}
