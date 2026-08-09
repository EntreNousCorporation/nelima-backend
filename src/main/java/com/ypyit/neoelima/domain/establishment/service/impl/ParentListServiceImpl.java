package com.ypyit.neoelima.domain.establishment.service.impl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.domain.establishment.dto.ParentListItemDto;
import com.ypyit.neoelima.domain.establishment.entity.QEstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.entity.QLevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.entity.QSchoolClassEntity;
import com.ypyit.neoelima.domain.establishment.entity.QStudentEntity;
import com.ypyit.neoelima.domain.establishment.service.ParentListService;
import com.ypyit.neoelima.domain.user.dto.ContactDto;
import com.ypyit.neoelima.domain.user.entity.QContactEntity;
import com.ypyit.neoelima.domain.user.entity.QTranslateEntity;
import com.ypyit.neoelima.domain.user.entity.QUserEntity;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Listes de parents, en un nombre fixe de requêtes quel que soit le nombre de parents rendus.
 *
 * <p>La page de parents se résout d'abord — identité et ordre —, puis leurs contacts et leurs
 * enfants se chargent en une requête groupée chacun, sur le modèle de
 * {@code StudentServiceImpl.attachBalances} et de {@code ParentDashboardService}. Jamais une requête
 * par parent : la forme se tient à deux parents et se voit à cent, et c'est le genre de code qu'on
 * n'a plus l'occasion de reprendre une fois en production.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParentListServiceImpl implements ParentListService {

    private final JPAQueryFactory queryFactory;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public Page<ParentListItemDto> searchForEstablishment(String keyword, Pageable pageable) {
        // La portée vient de l'utilisateur authentifié, jamais du client : un utilisateur
        // d'établissement est épinglé au sien. null ne survient que pour un admin YPYit — il voit
        // alors tout le parc, ses accès se jouant au filtre de sécurité.
        UUID scope = this.currentUserProvider.resolveEstablishmentScope(null);
        return this.search(keyword, scope, scope, pageable);
    }

    @Override
    public Page<ParentListItemDto> searchForPlatform(String keyword, UUID establishmentId, Pageable pageable) {
        // Le filtre établissement ne retient que les parents ayant un enfant dans cette école, mais
        // ne restreint pas la liste de leurs enfants : l'opérateur voit la famille entière, avec le
        // nom de chaque établissement.
        return this.search(keyword, establishmentId, null, pageable);
    }

    /**
     * Cœur commun aux deux listes.
     *
     * @param parentScope   établissement dans lequel le parent doit avoir un enfant pour apparaître ;
     *                      {@code null} pour ne pas restreindre la sélection des parents
     * @param childrenScope établissement auquel limiter les enfants affichés ; {@code null} pour
     *                      montrer tous les enfants de la famille
     */
    private Page<ParentListItemDto> search(String keyword, UUID parentScope, UUID childrenScope,
                                           Pageable pageable) {
        QStudentEntity student = QStudentEntity.studentEntity;
        QUserEntity parent = QUserEntity.userEntity;

        BooleanBuilder where = new BooleanBuilder();
        if (Objects.nonNull(parentScope)) {
            where.and(student.establishment.id.eq(parentScope));
        }
        if (StringUtils.isNotBlank(keyword)) {
            String kw = keyword.trim();
            // Le contact se cherche par un exists sur la collection, pour ne pas multiplier les
            // lignes du parent — un parent à trois contacts ne doit pas peser trois fois la page.
            where.and(parent.firstName.containsIgnoreCase(kw)
                    .or(parent.lastName.containsIgnoreCase(kw))
                    .or(parent.contacts.any().value.containsIgnoreCase(kw)));
        }

        long total = Objects.requireNonNullElse(this.queryFactory
                .select(parent.id.countDistinct())
                .from(student)
                .join(student.parentUsers, parent)
                .where(where)
                .fetchOne(), 0L);

        if (total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // La page de parents : identité et ordre en une requête. distinct car un parent réapparaît
        // pour chacun de ses enfants dans la portée.
        List<Tuple> parentRows = this.queryFactory
                .select(parent.id, parent.firstName, parent.lastName)
                .distinct()
                .from(student)
                .join(student.parentUsers, parent)
                .where(where)
                .orderBy(parent.lastName.asc(), parent.firstName.asc(), parent.id.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        List<UUID> ids = parentRows.stream().map(row -> row.get(parent.id)).toList();

        Map<UUID, List<ContactDto>> contactsByParent = this.contactsOf(ids);
        Map<UUID, List<ParentListItemDto.ParentListChildDto>> childrenByParent =
                this.childrenOf(ids, childrenScope);

        List<ParentListItemDto> content = new ArrayList<>(parentRows.size());
        for (Tuple row : parentRows) {
            UUID id = row.get(parent.id);
            List<ContactDto> contacts = contactsByParent.getOrDefault(id, List.of());
            List<ParentListItemDto.ParentListChildDto> children =
                    childrenByParent.getOrDefault(id, List.of());
            content.add(ParentListItemDto.builder()
                    .id(id)
                    .firstName(row.get(parent.firstName))
                    .lastName(row.get(parent.lastName))
                    .username(primaryContactValue(contacts))
                    .contacts(contacts)
                    .childrenCount(children.size())
                    .children(children)
                    .build());
        }
        return new PageImpl<>(content, pageable, total);
    }

    /** Les contacts de chaque parent, en une requête groupée. */
    private Map<UUID, List<ContactDto>> contactsOf(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        QUserEntity parent = QUserEntity.userEntity;
        QContactEntity contact = QContactEntity.contactEntity;

        List<Tuple> rows = this.queryFactory
                .select(parent.id, contact.id, contact.type, contact.value,
                        contact.isPrimary, contact.whatsApp)
                .from(parent)
                .join(parent.contacts, contact)
                .where(parent.id.in(ids))
                .fetch();

        Map<UUID, List<ContactDto>> result = new LinkedHashMap<>();
        for (Tuple row : rows) {
            ContactDto dto = ContactDto.builder()
                    .id(row.get(contact.id))
                    .type(row.get(contact.type))
                    .value(row.get(contact.value))
                    .isPrimary(Boolean.TRUE.equals(row.get(contact.isPrimary)))
                    .whatsApp(Boolean.TRUE.equals(row.get(contact.whatsApp)))
                    .build();
            result.computeIfAbsent(row.get(parent.id), k -> new ArrayList<>()).add(dto);
        }
        return result;
    }

    /**
     * Les enfants de chaque parent, en une requête groupée.
     *
     * <p>Toutes les jointures descendantes sont externes : un élève sans classe, sans niveau ou dont
     * l'établissement a été détaché doit rester visible dans sa famille.
     */
    private Map<UUID, List<ParentListItemDto.ParentListChildDto>> childrenOf(List<UUID> ids,
                                                                             UUID childrenScope) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        QStudentEntity student = QStudentEntity.studentEntity;
        QUserEntity parent = QUserEntity.userEntity;
        QSchoolClassEntity schoolClass = QSchoolClassEntity.schoolClassEntity;
        QLevelOfStudyEntity level = QLevelOfStudyEntity.levelOfStudyEntity;
        QTranslateEntity levelName = QTranslateEntity.translateEntity;
        QEstablishmentEntity establishment = QEstablishmentEntity.establishmentEntity;

        BooleanBuilder where = new BooleanBuilder();
        where.and(parent.id.in(ids));
        if (Objects.nonNull(childrenScope)) {
            where.and(student.establishment.id.eq(childrenScope));
        }

        List<Tuple> rows = this.queryFactory
                .select(parent.id, student.id, student.firstName, student.lastName,
                        student.registrationNumber, schoolClass.name, levelName.fr, establishment.name)
                .from(student)
                .join(student.parentUsers, parent)
                .leftJoin(student.schoolClass, schoolClass)
                .leftJoin(student.levelOfStudy, level)
                .leftJoin(level.name, levelName)
                .leftJoin(student.establishment, establishment)
                .where(where)
                .orderBy(student.lastName.asc(), student.firstName.asc())
                .fetch();

        Map<UUID, List<ParentListItemDto.ParentListChildDto>> result = new LinkedHashMap<>();
        for (Tuple row : rows) {
            ParentListItemDto.ParentListChildDto child = ParentListItemDto.ParentListChildDto.builder()
                    .id(row.get(student.id))
                    .firstName(row.get(student.firstName))
                    .lastName(row.get(student.lastName))
                    .registrationNumber(row.get(student.registrationNumber))
                    .className(row.get(schoolClass.name))
                    .levelLabel(row.get(levelName.fr))
                    .establishmentName(row.get(establishment.name))
                    .build();
            result.computeIfAbsent(row.get(parent.id), k -> new ArrayList<>()).add(child);
        }
        return result;
    }

    private static String primaryContactValue(List<ContactDto> contacts) {
        return contacts.stream()
                .filter(ContactDto::isPrimary)
                .map(ContactDto::getValue)
                .findFirst()
                .orElse(null);
    }
}
