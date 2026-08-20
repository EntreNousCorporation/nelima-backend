package com.ypyit.neoelima.domain.establishment.service;

import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.ActivityDto;
import com.ypyit.neoelima.domain.establishment.dto.SchoolClassLiteDto;
import com.ypyit.neoelima.domain.establishment.entity.ActivityEntity;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.FeeEntity;
import com.ypyit.neoelima.domain.establishment.entity.SchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.StaffEntity;
import com.ypyit.neoelima.domain.establishment.enums.ActivityStatus;
import com.ypyit.neoelima.domain.establishment.enums.EnrollmentStatus;
import com.ypyit.neoelima.domain.establishment.form.ActivityClassesForm;
import com.ypyit.neoelima.domain.establishment.form.ActivityForm;
import com.ypyit.neoelima.domain.establishment.repository.ActivityEnrollmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.ActivityRepository;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.FeeRepository;
import com.ypyit.neoelima.domain.establishment.repository.SchoolClassRepository;
import com.ypyit.neoelima.domain.establishment.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Catalogue des activités extra-scolaires.
 *
 * <p>Une activité est facultative : sa dette naît de l'inscription et d'elle seule. C'est ce qui
 * interdit de la traiter comme un frais ordinaire, dont le seul ciblage est le niveau scolaire et
 * qui facture d'office tous les élèves de ce niveau.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final ActivityEnrollmentRepository enrollmentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StaffRepository staffRepository;
    private final FeeRepository feeRepository;
    private final EstablishmentRepository establishmentRepository;
    private final CurrentUserProvider currentUserProvider;

    public List<ActivityDto> findAll() {
        return this.activityRepository.findByEstablishment_IdOrderByNameAsc(this.scope()).stream()
                .map(this::toDto).toList();
    }

    public ActivityDto findById(UUID id) {
        return this.toDto(this.load(id));
    }

    @Transactional
    public ActivityDto create(ActivityForm form) {
        UUID scope = this.scope();
        EstablishmentEntity establishment = this.establishmentRepository.findById(scope)
                .orElseThrow(() -> new NotFoundException("Establishment not found"));

        String name = form.getName().trim();
        if (this.activityRepository.existsByEstablishment_IdAndNameIgnoreCase(scope, name)) {
            throw new BadRequestException(String.format("Une activité nommée %s existe déjà.", name));
        }

        checkSlot(form);

        ActivityEntity entity = ActivityEntity.builder()
                .name(name)
                .kind(form.getKind())
                .place(trimToNull(form.getPlace()))
                .dayOfWeek(form.getDayOfWeek())
                .startTime(form.getStartTime())
                .endTime(form.getEndTime())
                .periodLabel(trimToNull(form.getPeriodLabel()))
                .capacity(form.getCapacity())
                .status(Objects.requireNonNullElse(form.getStatus(), ActivityStatus.DRAFT))
                .coach(this.resolveCoach(form.getCoachId(), establishment))
                .fee(this.createFeeFor(name, form.getPrice(), establishment))
                .establishment(establishment)
                .build();

        ActivityEntity saved = this.activityRepository.saveAndFlush(entity);
        log.info("ACTIVITY_CREATED: {} in establishment {}", saved.getId(), scope);
        return this.toDto(saved);
    }

    @Transactional
    public ActivityDto update(UUID id, ActivityForm form) {
        ActivityEntity entity = this.load(id);
        String name = form.getName().trim();
        if (!entity.getName().equalsIgnoreCase(name)
                && this.activityRepository.existsByEstablishment_IdAndNameIgnoreCase(
                        entity.getEstablishment().getId(), name)) {
            throw new BadRequestException(String.format("Une activité nommée %s existe déjà.", name));
        }

        checkSlot(form);

        entity.setName(name);
        entity.setKind(form.getKind());
        entity.setPlace(trimToNull(form.getPlace()));
        entity.setDayOfWeek(form.getDayOfWeek());
        entity.setStartTime(form.getStartTime());
        entity.setEndTime(form.getEndTime());
        entity.setPeriodLabel(trimToNull(form.getPeriodLabel()));
        entity.setCoach(this.resolveCoach(form.getCoachId(), entity.getEstablishment()));
        if (Objects.nonNull(form.getStatus())) {
            entity.setStatus(form.getStatus());
        }
        this.applyCapacity(entity, form.getCapacity());
        this.applyPrice(entity, form.getPrice());

        this.activityRepository.saveAndFlush(entity);
        return this.findById(id);
    }

    /**
     * Suspend l'activité au lieu de la supprimer.
     *
     * <p>Les inscriptions et les dettes qu'elles ont produites subsistent : effacer l'activité
     * rendrait orphelines des tranches déjà réglées. La suppression n'est possible que tant que
     * personne ne s'est inscrit, pour rattraper une saisie erronée.
     */
    @Transactional
    public void suspend(UUID id) {
        ActivityEntity entity = this.load(id);
        boolean untouched = this.enrollmentRepository
                .findByActivity_IdAndStatusIn(id, List.of(
                        EnrollmentStatus.ENROLLED, EnrollmentStatus.WAITLISTED, EnrollmentStatus.CANCELLED))
                .isEmpty();
        if (untouched) {
            this.activityRepository.delete(entity);
            log.info("ACTIVITY_DELETED: {}", id);
            return;
        }
        entity.setStatus(ActivityStatus.SUSPENDED);
        this.activityRepository.saveAndFlush(entity);
        log.info("ACTIVITY_SUSPENDED: {}", id);
    }

    /**
     * Remplace l'affectation aux classes.
     *
     * <p>Un remplacement et non un ajout : l'écran présente des cases à cocher, et une opération
     * d'ajout obligerait le client à deviner ce qui a été décoché.
     */
    @Transactional
    public ActivityDto assignClasses(UUID id, ActivityClassesForm form) {
        ActivityEntity entity = this.load(id);
        entity.setOpenToAll(form.isOpenToAll());
        entity.getEligibleClasses().clear();

        if (!form.isOpenToAll()) {
            for (UUID classId : form.getClassIds()) {
                SchoolClassEntity schoolClass = this.schoolClassRepository.findById(classId)
                        .orElseThrow(() -> new NotFoundException(
                                String.format("Class with provided id %s not found", classId)));
                // L'identifiant vient du client : sans ce contrôle, une école ouvrirait son
                // activité à la classe d'une autre.
                if (!schoolClass.getEstablishment().getId().equals(entity.getEstablishment().getId())) {
                    throw new NotFoundException(String.format("Class with provided id %s not found", classId));
                }
                entity.getEligibleClasses().add(schoolClass);
            }
        }

        this.activityRepository.saveAndFlush(entity);
        return this.findById(id);
    }

    /**
     * Charge une activité du périmètre de l'appelant.
     *
     * <p>Répond {@code NotFound} — et non {@code Forbidden} — pour l'activité d'une autre école :
     * distinguer les deux révélerait son existence.
     */
    ActivityEntity load(UUID id) {
        ActivityEntity entity = this.activityRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Activity with provided id %s not found", id)));
        UUID scope = this.currentUserProvider.hasEstablishmentScope() ? this.scope() : null;
        if (Objects.nonNull(scope) && !entity.getEstablishment().getId().equals(scope)) {
            throw new NotFoundException(String.format("Activity with provided id %s not found", id));
        }
        return entity;
    }

    UUID scope() {
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        if (Objects.isNull(scope)) {
            throw new BadRequestException("Cette opération suppose un compte d'établissement.");
        }
        return scope;
    }

    /**
     * Crée le frais qui porte le tarif.
     *
     * <p>Écrit directement par le dépôt, sans niveau ciblé et marqué facultatif. Passer par le
     * service des frais serait un contresens : il exige des niveaux et facture aussitôt tous leurs
     * élèves, alors qu'ici la dette doit naître de l'inscription.
     */
    private FeeEntity createFeeFor(String name, BigDecimal price, EstablishmentEntity establishment) {
        if (Objects.isNull(price) || price.signum() <= 0) {
            return null;
        }
        return this.feeRepository.saveAndFlush(FeeEntity.builder()
                .name(name)
                .price(price)
                .optional(true)
                // Hors scolarité : le frais d'activité ne se compte pas dans la scolarité annuelle.
                .academical(false)
                .establishment(establishment)
                .build());
    }

    /**
     * Applique le tarif à un frais existant, ou en crée un.
     *
     * <p>Le tarif ne se modifie plus dès qu'une dette est née : les élèves déjà inscrits ont été
     * facturés au montant d'alors, et le changer ici ferait diverger leur dette de ce que dit
     * l'écran sans rien corriger.
     */
    private void applyPrice(ActivityEntity entity, BigDecimal price) {
        FeeEntity fee = entity.getFee();
        boolean wanted = Objects.nonNull(price) && price.signum() > 0;

        if (Objects.isNull(fee)) {
            if (wanted) {
                entity.setFee(this.createFeeFor(entity.getName(), price, entity.getEstablishment()));
            }
            return;
        }

        long billed = this.enrollmentRepository
                .countByActivity_IdAndStatus(entity.getId(), EnrollmentStatus.ENROLLED);
        if (billed > 0 && (!wanted || fee.getPrice().compareTo(price) != 0)) {
            throw new BadRequestException(String.format(
                    "Le tarif ne peut plus changer : %d élève(s) ont déjà été facturés.", billed));
        }
        if (wanted) {
            fee.setName(entity.getName());
            fee.setPrice(price);
            this.feeRepository.saveAndFlush(fee);
        }
    }

    /** Réduire la capacité en dessous des inscrits ferait sortir quelqu'un sans qu'on l'ait dit. */
    private void applyCapacity(ActivityEntity entity, Integer capacity) {
        long enrolled = this.enrollmentRepository
                .countByActivity_IdAndStatus(entity.getId(), EnrollmentStatus.ENROLLED);
        if (capacity < enrolled) {
            throw new BadRequestException(String.format(
                    "La capacité ne peut pas descendre sous les %d inscrit(s).", enrolled));
        }
        entity.setCapacity(capacity);
    }

    /**
     * Refuse un créneau qui ne tient pas debout.
     *
     * <p>Le créneau est facultatif — une activité peut naître sans horaire, à fixer plus tard.
     * Mais à moitié rempli il ment : le catalogue affiche « 16:00 – » sans fin. Et une fin qui
     * précède le début est une inversion de saisie, pas une activité qui court après minuit :
     * l'horaire d'une activité extra-scolaire tient dans la journée d'école.
     */
    private static void checkSlot(ActivityForm form) {
        LocalTime start = form.getStartTime();
        LocalTime end = form.getEndTime();
        if (Objects.isNull(start) && Objects.isNull(end)) {
            return;
        }
        if (Objects.isNull(start) || Objects.isNull(end)) {
            throw new BadRequestException("Le créneau demande un début et une fin, ou aucun des deux.");
        }
        if (!end.isAfter(start)) {
            throw new BadRequestException("La fin du créneau doit venir après le début.");
        }
    }

    private StaffEntity resolveCoach(UUID coachId, EstablishmentEntity establishment) {
        if (Objects.isNull(coachId)) {
            return null;
        }
        StaffEntity coach = this.staffRepository.findById(coachId)
                .orElseThrow(() -> new BadRequestException("Ce membre du personnel n'existe pas."));
        if (!coach.getEstablishment().getId().equals(establishment.getId())) {
            throw new BadRequestException("Ce membre du personnel n'existe pas.");
        }
        if (!coach.isActive()) {
            throw new BadRequestException(String.format(
                    "%s ne fait plus partie du personnel actif.", coach.getLastName()));
        }
        return coach;
    }

    ActivityDto toDto(ActivityEntity entity) {
        long enrolled = this.enrollmentRepository
                .countByActivity_IdAndStatus(entity.getId(), EnrollmentStatus.ENROLLED);
        long waitlisted = this.enrollmentRepository
                .countByActivity_IdAndStatus(entity.getId(), EnrollmentStatus.WAITLISTED);
        FeeEntity fee = entity.getFee();
        StaffEntity coach = entity.getCoach();

        return ActivityDto.builder()
                .id(entity.getId().toString())
                .name(entity.getName())
                .kind(entity.getKind())
                .place(entity.getPlace())
                .dayOfWeek(entity.getDayOfWeek())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .periodLabel(entity.getPeriodLabel())
                .capacity(entity.getCapacity())
                .status(entity.getStatus())
                .coachId(Objects.isNull(coach) ? null : coach.getId().toString())
                .coachName(Objects.isNull(coach) ? null
                        : String.format("%s %s", coach.getLastName(), coach.getFirstName()))
                .openToAll(entity.isOpenToAll())
                .eligibleClasses(entity.getEligibleClasses().stream()
                        .sorted(Comparator.comparing(SchoolClassEntity::getName))
                        .map(schoolClass -> SchoolClassLiteDto.builder()
                                .id(schoolClass.getId().toString())
                                .name(schoolClass.getName())
                                .room(schoolClass.getRoom())
                                .build())
                        .toList())
                .price(Objects.isNull(fee) ? null : fee.getPrice())
                .feeId(Objects.isNull(fee) ? null : fee.getId().toString())
                .enrolledCount(enrolled)
                .waitlistedCount(waitlisted)
                .remainingSeats(Math.max(0, entity.getCapacity() - enrolled))
                .expectedRevenue(Objects.isNull(fee) ? null
                        : fee.getPrice().multiply(BigDecimal.valueOf(enrolled)))
                .build();
    }

    private static String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}
