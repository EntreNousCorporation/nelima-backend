package com.ypyit.neoelima.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.ypyit.neoelima.domain.establishment.entity.LevelOfStudyEntity;
import com.ypyit.neoelima.domain.establishment.enums.EducationCycle;
import com.ypyit.neoelima.domain.establishment.repository.LevelOfStudyRepository;
import com.ypyit.neoelima.domain.transverse.entity.GlobalParameterEntity;
import com.ypyit.neoelima.domain.transverse.repository.GlobalParameterRepository;
import com.ypyit.neoelima.domain.user.dto.TranslateDto;
import com.ypyit.neoelima.domain.user.entity.AdminUserEntity;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.PasswordEntity;
import com.ypyit.neoelima.domain.user.entity.PermissionEntity;
import com.ypyit.neoelima.domain.user.entity.RoleEntity;
import com.ypyit.neoelima.domain.user.entity.TranslateEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.mapper.TranslateMapper;
import com.ypyit.neoelima.domain.user.repository.PermissionRepository;
import com.ypyit.neoelima.domain.user.repository.RoleRepository;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import com.ypyit.neoelima.domain.utils.ParameterUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@Transactional
@RequiredArgsConstructor
@ConditionalOnProperty(value = "nelima.initialize-data", havingValue = "true")
public class InitParameter implements CommandLineRunner {

    @Value("${nelima.default-admin-pwd}")
    private String defaultAdminPwd;

    private final PermissionRepository permissionRepository;
    private final TranslateMapper translateMapper;
    private final RoleRepository roleRepository;
    private final GlobalParameterRepository globalParameterRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final LevelOfStudyRepository levelOfStudyRepository;

    @Override
    public void run(String... args) throws Exception {
        this.initPermissions();
        this.initRoles();
        this.initGlobalParameters();
        this.initUsers();
        this.initLevelOfStudies();
    }


    private void initPermissions() throws IOException {

        log.info("Begin --> permissions");

        JsonNode rootNode = ParameterUtils.getJsonNode("parameters/permissions.json");
        List<PermissionEntity> permissions = new ArrayList<>();

        rootNode.elements().forEachRemaining(node -> {
            JsonNode name = node.get("name");
            TranslateDto translateDto = ParameterUtils.getTranslateFromJsonNode(name);
            String code = node.get("code").asText().trim();

            Optional<PermissionEntity> permission = Optional.empty();
            try {
                permission = this.permissionRepository.findByCode(code);
            } catch (Exception e) {
                log.error("Error while fetching permissions", e);
            }

            TranslateEntity translate = this.translateMapper.toEntity(translateDto);
            if (permission.isPresent()) {
                permission.get().getName().setFr(translate.getFr());
                permission.get().getName().setEn(translate.getEn());
            } else {
                permission = Optional.of(PermissionEntity.builder()
                        .code(code)
                        .name(translate)
                        .build());
            }
            permissions.add(permission.get());
        });
        this.permissionRepository.saveAll(permissions);

        log.info("End --> permissions");
    }

    private void initRoles() throws IOException {

        log.info("Begin --> roles");

        JsonNode rootNode = ParameterUtils.getJsonNode("parameters/roles.json");
        List<RoleEntity> roles = new ArrayList<>();

        rootNode.elements().forEachRemaining(node -> {
            JsonNode name = node.get("name");
            TranslateDto translateDTO = ParameterUtils.getTranslateFromJsonNode(name);
            String code = node.get("code").asText().trim();

            List<PermissionEntity> permissions = ParameterUtils.getValuesWithType(node.get("permissions"), PermissionEntity.class);

            List<PermissionEntity> collect = permissions.stream()
                    .map(functionality -> permissionRepository.findByCode(functionality.getCode()))
                    .filter(Optional::isPresent)
                    .map(Optional::get).toList();
            log.info(collect.toString());
            Optional<RoleEntity> role = Optional.empty();
            try {
                role = this.roleRepository.findByCode(code);
            } catch (Exception e) {
                log.error("Error while fetching role", e);
            }

            TranslateEntity translate = this.translateMapper.toEntity(translateDTO);
            if (role.isPresent()) {
                role.get().setName(translate);
            } else {
                role = Optional.of(RoleEntity.builder()
                        .code(code)
                        .name(translate)
                        .build());
            }
            role.get().setPermissions(new HashSet<>(collect));
            roles.add(role.get());
        });
        this.roleRepository.saveAll(roles);

        log.info("End --> roles");
    }

    private void initGlobalParameters() throws IOException {

        log.info("Begin --> global-parameters");

        JsonNode rootNode = ParameterUtils.getJsonNode("parameters/global-parameters.json");
        List<GlobalParameterEntity> globalParameters = new ArrayList<>();

        rootNode.elements().forEachRemaining(node -> {
            String name = node.get("name").asText();
            String code = node.get("code").asText();
            String value = node.get("value").asText();
            try {
                if (!this.globalParameterRepository.existsByCode(code)) {
                    GlobalParameterEntity globalParameter = GlobalParameterEntity.builder()
                            .name(name)
                            .code(code)
                            .value(value)
                            .createdAt(Instant.now())
                            .build();
                    globalParameters.add(globalParameter);
                }
            } catch (Exception e) {
                log.error("Error while saving global-parameters", e);
            }
            log.info("End --> global-parameters");
        });
        log.info("global-parameters {}", globalParameters);
        this.globalParameterRepository.saveAll(globalParameters);
    }

    private void initUsers() throws IOException {

        log.info("Begin --> users");

        JsonNode rootNode = ParameterUtils.getJsonNode("parameters/users.json");
        List<UserEntity> users = new ArrayList<>();

        rootNode.elements().forEachRemaining(node -> {
            String role = node.get("role").asText();
            String email = node.get("email").asText();
            String firstName = node.get("firstName").asText();
            String lastName = node.get("lastName").asText();
            String phone = node.get("phone").asText();

            try {
                if (!this.userRepository.existsByPrimaryContact(email)) {
                    Optional<RoleEntity> roleEntity = this.roleRepository.findByCode(role);
                    if (roleEntity.isEmpty()) {
                        log.error("Failed to saved users because role ADMIN  do not exists");
                        return;
                    }
                    Set<ContactEntity> contacts = Set.of(ContactEntity.builder()
                                    .isPrimary(true)
                                    .type(ContactType.EMAIL)
                                    .value(email)
                                    .build(),
                            ContactEntity.builder()
                                    .isPrimary(false)
                                    .type(ContactType.PHONE_NUMBER)
                                    .value(phone)
                                    .build());
                    if (!this.userRepository.existsByPrimaryContact(email)) {
                        AdminUserEntity user = AdminUserEntity.builder()
                                .firstName(firstName)
                                .lastName(lastName)
                                .contacts(contacts)
                                .role(roleEntity.get())
                                .passwordValue(PasswordEntity.builder()
                                        .value(this.passwordEncoder.encode(defaultAdminPwd)).build())
                                .build();
                        users.add(user);
                    }
                } else {
                    UserEntity user = this.userRepository.findByPrimaryContact(email).get();
                    // Le mot de passe n'est PAS réécrit ici.
                    //
                    // Il l'était, et à chaque démarrage : `InitParameter` est un `CommandLineRunner`
                    // et `initialize-data` vaut `true` en production, donc chaque `deploy.sh`
                    // rétablissait `DEFAULT_ADMIN_PWD` sur un compte qui atteint la configuration de
                    // l'agrégateur de paiement, le taux de commission et toutes les écoles.
                    //
                    // Deux conséquences : une rotation faite depuis l'application était annulée au
                    // déploiement suivant — silencieusement, ce qui est le pire des deux —, et le
                    // mot de passe devait vivre en clair dans le compose du VPS à perpétuité.
                    //
                    // L'amorçage garde son rôle : créer le compte s'il manque (branche du dessus).
                    // Il n'a jamais eu à le reprendre en main s'il existe.
                    user.setFirstName(firstName);
                    user.setLastName(lastName);
                    users.add(user);
                }
            } catch (Exception e) {
                log.error("Error while users", e);
            }
            log.info("End --> users");
        });
        log.info("users {}", users);
        this.userRepository.saveAll(users);
    }

    private void initLevelOfStudies() throws IOException {

        log.info("Begin --> level of studies");

        JsonNode rootNode = ParameterUtils.getJsonNode("parameters/level-of-studies.json");
        List<LevelOfStudyEntity> levelOfStudies = new ArrayList<>();

        rootNode.elements().forEachRemaining(node -> {
            JsonNode name = node.get("name");
            TranslateDto translateDTO = ParameterUtils.getTranslateFromJsonNode(name);
            String code = node.get("code").asText().trim();
            String previous = node.get("previous").asText().trim();
            String next = node.get("next").asText().trim();
            int position = node.get("position").asInt();
            // Le cycle vient du catalogue et non de la seule migration : sur une base neuve, les
            // niveaux sont créés ici, après le backfill, et repartiraient sans cycle.
            EducationCycle cycle = EducationCycle.valueOf(node.get("cycle").asText().trim());
            Optional<LevelOfStudyEntity> levelOfStudy = Optional.empty();
            try {
                levelOfStudy = this.levelOfStudyRepository.findByCode(code);
            } catch (Exception e) {
                log.error("Error while fetching level of studies", e);
            }

            TranslateEntity translate = this.translateMapper.toEntity(translateDTO);
            if (levelOfStudy.isPresent()) {
                levelOfStudy.get().setName(translate);
                levelOfStudy.get().setCycle(cycle);
            } else {
                levelOfStudy = Optional.of(LevelOfStudyEntity.builder()
                        .code(code)
                        .name(translate)
                        .previous(previous)
                        .position(position)
                        .next(next)
                        .cycle(cycle)
                        .build());
            }
            levelOfStudies.add(levelOfStudy.get());
        });
        this.levelOfStudyRepository.saveAll(levelOfStudies);

        log.info("End --> level of studies");
    }
}
