package com.ypyit.neoelima.domain.canteen.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.canteen.dto.CanteenDtos;
import com.ypyit.neoelima.domain.canteen.entity.CanteenMenuDayEntity;
import com.ypyit.neoelima.domain.canteen.form.CanteenForms;
import com.ypyit.neoelima.domain.canteen.repository.CanteenMenuDayRepository;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.StudentEntity;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Le menu de la cantine, semaine par semaine.
 *
 * <p>Il se saisit jour par jour et se publie à la semaine : une semaine publiée est visible des
 * familles. La modifier ensuite la laisse publiée — corriger un plat n'a pas à la retirer.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CanteenMenuService {

    /** Jours de cantine affichés : la semaine scolaire. */
    private static final List<DayOfWeek> SCHOOL_DAYS = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private final CanteenMenuDayRepository menuRepository;
    private final EstablishmentRepository establishmentRepository;
    private final StudentRepository studentRepository;
    private final CurrentUserProvider currentUserProvider;

    /** La semaine qui contient ce jour, du lundi au vendredi, jours sans menu compris. */
    public List<CanteenDtos.MenuDay> week(LocalDate anyDay) {
        return this.week(this.scope(), anyDay, false);
    }

    @Transactional
    public List<CanteenDtos.MenuDay> save(CanteenForms.Menu form) {
        UUID scope = this.scope();
        EstablishmentEntity establishment = this.establishmentRepository.getReferenceById(scope);
        LocalDate anyDay = null;
        for (CanteenForms.MenuDay day : form.getDays()) {
            anyDay = day.getDay();
            CanteenMenuDayEntity entity = this.menuRepository.findByEstablishment_IdAndDay(scope, day.getDay())
                    .orElseGet(() -> CanteenMenuDayEntity.builder().establishment(establishment).day(day.getDay()).build());
            entity.setStarter(trim(day.getStarter()));
            entity.setMainCourse(trim(day.getMainCourse()));
            entity.setDessert(trim(day.getDessert()));
            entity.setVegetarianAlternative(trim(day.getVegetarianAlternative()));
            entity.setClosed(day.isClosed());
            entity.setAllergens(Objects.isNull(day.getAllergens()) || day.getAllergens().isEmpty() ? null
                    : day.getAllergens().stream().map(String::trim).filter(StringUtils::isNotBlank).distinct()
                    .collect(Collectors.joining(",")));
            this.menuRepository.save(entity);
        }
        if (Objects.isNull(anyDay)) {
            throw new BadRequestException("Le menu ne contient aucun jour.");
        }
        return this.week(scope, anyDay, false);
    }

    /** Publie (ou retire) la semaine : les familles la voient dans l'application. */
    @Transactional
    public List<CanteenDtos.MenuDay> publish(LocalDate anyDay, boolean published) {
        UUID scope = this.scope();
        LocalDate monday = anyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<CanteenMenuDayEntity> days = this.menuRepository
                .findByEstablishment_IdAndDayBetweenOrderByDayAsc(scope, monday, monday.plusDays(6));
        if (published && days.isEmpty()) {
            throw new BadRequestException("Saisissez le menu avant de le publier.");
        }
        days.forEach(day -> day.setPublished(published));
        this.menuRepository.saveAll(days);
        return this.week(scope, anyDay, false);
    }

    /** Le menu publié de l'école de chacun des enfants d'un parent. */
    public Map<String, List<CanteenDtos.MenuDay>> mine(LocalDate anyDay) {
        UUID parentId = this.currentUserProvider.currentUser().getId();
        LocalDate day = Objects.requireNonNullElse(anyDay, LocalDate.now());
        return this.studentRepository.findByParentUsers_Id(parentId).stream()
                .map(StudentEntity::getEstablishment).filter(Objects::nonNull).distinct()
                .collect(Collectors.toMap(EstablishmentEntity::getName,
                        school -> this.week(school.getId(), day, true), (a, b) -> a));
    }

    private List<CanteenDtos.MenuDay> week(UUID scope, LocalDate anyDay, boolean publishedOnly) {
        LocalDate monday = Objects.requireNonNullElse(anyDay, LocalDate.now()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<LocalDate, CanteenMenuDayEntity> byDay = this.menuRepository
                .findByEstablishment_IdAndDayBetweenOrderByDayAsc(scope, monday, monday.plusDays(6)).stream()
                .filter(day -> !publishedOnly || day.isPublished())
                .collect(Collectors.toMap(CanteenMenuDayEntity::getDay, Function.identity()));
        List<CanteenDtos.MenuDay> week = new ArrayList<>();
        for (DayOfWeek dow : SCHOOL_DAYS) {
            LocalDate date = monday.with(TemporalAdjusters.nextOrSame(dow));
            CanteenMenuDayEntity entity = byDay.get(date);
            if (publishedOnly && Objects.isNull(entity)) {
                continue;
            }
            week.add(CanteenDtos.MenuDay.builder()
                    .day(date)
                    .starter(Objects.isNull(entity) ? null : entity.getStarter())
                    .mainCourse(Objects.isNull(entity) ? null : entity.getMainCourse())
                    .dessert(Objects.isNull(entity) ? null : entity.getDessert())
                    .vegetarianAlternative(Objects.isNull(entity) ? null : entity.getVegetarianAlternative())
                    .allergens(Objects.isNull(entity) || StringUtils.isBlank(entity.getAllergens()) ? List.of()
                            : Arrays.asList(entity.getAllergens().split(",")))
                    .published(Objects.nonNull(entity) && entity.isPublished())
                    .closed(Objects.nonNull(entity) && entity.isClosed())
                    .build());
        }
        return week;
    }

    private UUID scope() {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        return scope;
    }

    private static String trim(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}
