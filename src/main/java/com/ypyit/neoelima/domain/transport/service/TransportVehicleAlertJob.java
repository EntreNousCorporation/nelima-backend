package com.ypyit.neoelima.domain.transport.service;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ypyit.neoelima.common.service.email.service.EmailConstants;
import com.ypyit.neoelima.common.service.email.enums.EmailTemplateType;
import com.ypyit.neoelima.common.service.email.service.EmailDefaultProperties;
import com.ypyit.neoelima.common.service.email.service.EmailService;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.service.SchoolLogo;
import com.ypyit.neoelima.domain.transport.entity.TransportLineEntity;
import com.ypyit.neoelima.domain.transport.entity.TransportVehicleEntity;
import com.ypyit.neoelima.domain.transport.repository.TransportLineRepository;
import com.ypyit.neoelima.domain.transport.repository.TransportVehicleRepository;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.QEstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Prévient la direction d'une école, trente jours à l'avance, que l'assurance ou la visite
 * technique d'un véhicule arrive à échéance.
 *
 * <p>Chaque échéance n'est signalée qu'une fois : le véhicule garde la date déjà annoncée. Une
 * nouvelle date saisie après renouvellement réarme l'alerte pour l'échéance suivante. Un véhicule
 * enregistré alors que l'échéance est déjà proche, ou passée, est signalé dès le lendemain matin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransportVehicleAlertJob {

    static final int DAYS_BEFORE = 30;
    private static final ZoneId ABIDJAN = ZoneId.of("Africa/Abidjan");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Set<String> DIRECTION = Set.of("ESTABLISHMENT_ROOT", "DIRECTEUR");

    private final TransportVehicleRepository vehicleRepository;
    private final TransportLineRepository lineRepository;
    private final JPAQueryFactory queryFactory;
    private final EmailService emailService;
    private final EmailDefaultProperties emailDefaultProperties;
    private final SchoolLogo schoolLogo;

    @Scheduled(cron = "${nelima.transport.vehicle-alert-cron:0 30 7 * * *}", zone = "Africa/Abidjan")
    @Transactional
    public void alertUpcomingDeadlines() {
        this.run(LocalDate.now(ABIDJAN));
    }

    /** Une passe pour le jour donné ; renvoie le nombre d'échéances signalées. */
    @Transactional
    public int run(LocalDate today) {
        LocalDate horizon = today.plusDays(DAYS_BEFORE);
        Map<UUID, List<Map<String, Object>>> itemsBySchool = new LinkedHashMap<>();
        Map<UUID, EstablishmentEntity> schools = new LinkedHashMap<>();
        int count = 0;
        for (TransportVehicleEntity vehicle : this.vehicleRepository.findByActiveTrue()) {
            List<Map<String, Object>> items = new ArrayList<>();
            LocalDate insurance = vehicle.getInsuranceExpiry();
            if (due(insurance, vehicle.getInsuranceAlertedFor(), horizon)) {
                items.add(item(vehicle, "Assurance", insurance, today));
                vehicle.setInsuranceAlertedFor(insurance);
            }
            LocalDate visit = vehicle.getTechnicalVisit();
            if (due(visit, vehicle.getTechnicalVisitAlertedFor(), horizon)) {
                items.add(item(vehicle, "Visite technique", visit, today));
                vehicle.setTechnicalVisitAlertedFor(visit);
            }
            if (items.isEmpty()) {
                continue;
            }
            String lines = this.lineRepository.findByEstablishment_IdOrderByNameAsc(vehicle.getEstablishment().getId()).stream()
                    .filter(l -> l.isActive() && Objects.nonNull(l.getVehicle()) && l.getVehicle().getId().equals(vehicle.getId()))
                    .map(TransportLineEntity::getName).collect(Collectors.joining(", "));
            items.forEach(i -> i.put("lines", lines.isEmpty() ? null : lines));
            UUID schoolId = vehicle.getEstablishment().getId();
            schools.putIfAbsent(schoolId, vehicle.getEstablishment());
            itemsBySchool.computeIfAbsent(schoolId, k -> new ArrayList<>()).addAll(items);
            count += items.size();
            this.vehicleRepository.save(vehicle);
        }
        itemsBySchool.forEach((schoolId, items) -> this.notifyDirection(schools.get(schoolId), items));
        if (count > 0) {
            log.info("TRANSPORT_VEHICLE_ALERTS: {} échéance(s) signalée(s) à {} école(s)", count, itemsBySchool.size());
        }
        return count;
    }

    private void notifyDirection(EstablishmentEntity school, List<Map<String, Object>> items) {
        Set<String> recipients = this.directionEmails(school.getId());
        if (recipients.isEmpty()) {
            log.warn("TRANSPORT_VEHICLE_ALERT_NO_RECIPIENT: aucune adresse de direction pour l'établissement {}", school.getId());
            return;
        }
        for (String email : recipients) {
            Context context = this.emailDefaultProperties.getDefaultContext();
            context.setVariable(EmailConstants.EMAIL, email);
            context.setVariable("items", items);
            this.schoolLogo.addTo(context, school);
            this.emailService.send(context, EmailTemplateType.TRANSPORT_VEHICLE_EXPIRY);
        }
    }

    /** Adresses des comptes actifs de la direction : administrateur d'établissement et directeurs. */
    private Set<String> directionEmails(UUID schoolId) {
        QEstablishmentUserEntity user = QEstablishmentUserEntity.establishmentUserEntity;
        List<EstablishmentUserEntity> users = this.queryFactory.selectFrom(user)
                .where(user.establishment.id.eq(schoolId), user.enabled.isTrue(), user.role.code.in(DIRECTION))
                .fetch();
        Set<String> emails = new LinkedHashSet<>();
        for (EstablishmentUserEntity u : users) {
            u.getContacts().stream()
                    .filter(c -> c.getType() == ContactType.EMAIL)
                    .sorted((a, b) -> Boolean.compare(b.isPrimary(), a.isPrimary()))
                    .map(ContactEntity::getValue).findFirst()
                    .ifPresent(e -> emails.add(e.trim().toLowerCase()));
        }
        return emails;
    }

    private static boolean due(LocalDate deadline, LocalDate alertedFor, LocalDate horizon) {
        return Objects.nonNull(deadline) && !deadline.isAfter(horizon) && !deadline.equals(alertedFor);
    }

    private static Map<String, Object> item(TransportVehicleEntity vehicle, String label, LocalDate deadline, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, deadline);
        String when = days < 0 ? "expirée depuis le " + deadline.format(DAY)
                : days == 0 ? "expire aujourd'hui"
                : "expire le " + deadline.format(DAY) + " (dans " + days + " jour" + (days > 1 ? "s" : "") + ")";
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("plate", vehicle.getPlate());
        item.put("label", label);
        item.put("when", when);
        item.put("expired", days < 0);
        return item;
    }
}
