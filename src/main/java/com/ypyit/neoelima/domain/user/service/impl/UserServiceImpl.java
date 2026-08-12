package com.ypyit.neoelima.domain.user.service.impl;

import com.ypyit.neoelima.common.cache.CacheService;
import com.ypyit.neoelima.common.exception.BadRequestException;
import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.config.security.CurrentUserProvider;
import com.ypyit.neoelima.common.exception.DuplicateResourceException;
import com.ypyit.neoelima.common.exception.NotFoundException;
import com.ypyit.neoelima.common.exception.UnAuthenticatedUserException;
import com.ypyit.neoelima.common.exception.ValidationException;
import com.ypyit.neoelima.common.service.email.enums.EmailTemplateType;
import com.ypyit.neoelima.common.service.email.service.EmailConstants;
import com.ypyit.neoelima.common.service.email.service.EmailDefaultProperties;
import com.ypyit.neoelima.common.service.email.service.EmailService;
import com.ypyit.neoelima.common.service.sms.enums.SmsTemplateType;
import com.ypyit.neoelima.common.service.sms.service.SmsService;
import com.ypyit.neoelima.config.audit.CurrentLocale;
import com.ypyit.neoelima.config.properties.EmailConfigProperties;
import com.ypyit.neoelima.domain.authentication.dto.TokenIntrospection;
import com.ypyit.neoelima.domain.authentication.service.AuthService;
import com.ypyit.neoelima.domain.establishment.dto.StudentLiteDto;
import com.ypyit.neoelima.domain.establishment.entity.EstablishmentEntity;
import com.ypyit.neoelima.domain.establishment.mapper.StudentMapper;
import com.ypyit.neoelima.domain.establishment.repository.EstablishmentRepository;
import com.ypyit.neoelima.domain.establishment.repository.StudentRepository;
import com.ypyit.neoelima.domain.establishment.service.EstablishmentService;
import com.ypyit.neoelima.domain.transverse.dto.CheckResourceDto;
import com.ypyit.neoelima.domain.transverse.dto.CheckResourceStatus;
import com.ypyit.neoelima.domain.transverse.enums.GlobalParameterKey;
import com.ypyit.neoelima.domain.transverse.service.GlobalParameterService;
import com.ypyit.neoelima.domain.user.dto.UserDto;
import com.ypyit.neoelima.domain.user.entity.AdminUserEntity;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.PasswordEntity;
import com.ypyit.neoelima.domain.user.entity.RoleEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.enums.RoleTarget;
import com.ypyit.neoelima.domain.user.enums.RoleType;
import com.ypyit.neoelima.domain.user.enums.UserType;
import com.ypyit.neoelima.domain.user.form.ChangePasswordForm;
import com.ypyit.neoelima.domain.user.form.ContactCreationForm;
import com.ypyit.neoelima.domain.user.form.ContactUpdateForm;
import com.ypyit.neoelima.domain.user.form.EstablishmentUserSignupForm;
import com.ypyit.neoelima.domain.user.form.InitResetPasswordForm;
import com.ypyit.neoelima.domain.user.form.InitResetPasswordRequest;
import com.ypyit.neoelima.domain.user.form.MobileUserSignupForm;
import com.ypyit.neoelima.domain.user.form.ResetMobilePasswordForm;
import com.ypyit.neoelima.domain.user.form.ResetPasswordForm;
import com.ypyit.neoelima.domain.user.form.ResetPwdRequest;
import com.ypyit.neoelima.domain.user.form.UserSignupForm;
import com.ypyit.neoelima.domain.user.form.UserUpdateForm;
import com.ypyit.neoelima.domain.user.mapper.UserMapper;
import com.ypyit.neoelima.domain.user.repository.RoleRepository;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import com.ypyit.neoelima.domain.user.service.ContactService;
import com.ypyit.neoelima.domain.user.service.IdentityService;
import com.ypyit.neoelima.domain.user.service.UserService;
import com.ypyit.neoelima.domain.utils.CredentialsUtils;
import com.ypyit.neoelima.domain.utils.FunctionalUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.hibernate.Hibernate;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.thymeleaf.context.Context;

import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.ypyit.neoelima.common.service.email.enums.EmailTemplateType.RESEND_MOBILE_OTP;
import static com.ypyit.neoelima.common.service.email.enums.EmailTemplateType.RESET_MOBILE_PASSWORD;
import static com.ypyit.neoelima.common.service.email.enums.EmailTemplateType.WELCOME_MOBILE_USER;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String MOBILE_USER_KEY = "MOBILE_USER_KEY_%s";
    private static final String PARTNER_ROOT_USER_EMAIL_KEY = "PARTNER_ROOT_USER_EMAIL_KEY_%s";
    private static final int RESET_PWD_OTP_LENGTH = 6;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;
    private final IdentityService identityService;
    private final EstablishmentService establishmentService;
    private final EstablishmentRepository establishmentRepository;
    private final EmailDefaultProperties emailDefaultProperties;
    private final EmailService emailService;
    private final GlobalParameterService globalParameterService;
    private final AuthService authService;
    private final CacheService cacheService;
    private final EmailConfigProperties emailConfigProperties;
    private final ContactService contactService;
    private final MessageSource messageSource;
    private final SmsService smsService;
    private final StudentRepository studentRepository;
    private final StudentMapper studentMapper;

    @Override
    public UserDto createPartnerRootUser(UserSignupForm signupForm) throws BusinessException {
        try {
            this.validateContacts(signupForm.getContacts());
            RoleEntity role = this.roleRepository.findByCode(RoleType.ESTABLISHMENT_ROOT.name())
                    .orElseThrow(() -> new NotFoundException(String.format("Default role %s not found", RoleType.ESTABLISHMENT_ROOT.name())));
            EstablishmentUserEntity user = this.userMapper.toRootEntity(signupForm);
            user.setRole(role);
            EstablishmentUserEntity savedUser = this.userRepository.save(user);
            EstablishmentEntity establishment = this.establishmentService.create(signupForm.getEstablishment());
            savedUser.setEstablishment(establishment);
            establishment.setPrincipal(savedUser);
            this.establishmentRepository.save(establishment);
            this.sendResetPasswordEmail(InitResetPasswordRequest.builder()
                    .username(savedUser.getUsername())
                    .target(RoleTarget.PARTNER)
                    .user(user).build(), EmailTemplateType.WELCOME_USER);
            return this.userMapper.toDto(savedUser);
        } catch (DuplicateResourceException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public UserDto createPartnerUser(UUID establishmentId, EstablishmentUserSignupForm signupForm) throws BusinessException {
        try {
            this.validateContacts(signupForm.getContacts());
            EstablishmentEntity establishment = this.establishmentRepository.findById(establishmentId)
                    .orElseThrow(() -> new DuplicateResourceException(
                            String.format("Establishment with id %s does not found", establishmentId)));

            RoleEntity role = this.roleRepository.findById(signupForm.getRoleId())
                    .orElseThrow(() -> new NotFoundException(String.format("Role with id %s not found", signupForm.getRoleId())));

            EstablishmentUserEntity user = this.userMapper.toEntity(signupForm);
            user.setRole(role);
            user.setEstablishment(establishment);
            UserEntity savedUser = this.userRepository.save(user);
            this.sendResetPasswordEmail(InitResetPasswordRequest.builder()
                    .username(savedUser.getUsername())
                    .target(RoleTarget.PARTNER)
                    .user(user).build(), EmailTemplateType.WELCOME_USER);
            return this.userMapper.toDto(savedUser);
        } catch (DuplicateResourceException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public UserDto resendActivationLink(UUID establishmentId, UUID userId) throws BusinessException {
        try {
            UserEntity user = this.userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException(
                            String.format("Cannot find user with id %s", userId)));

            // L'appartenance se vérifie, elle ne se suppose pas : l'appelant a prouvé qu'il
            // administre `establishmentId`, rien d'autre. Sans ce contrôle, l'identifiant d'un
            // compte d'une autre école suffirait à lui faire expédier un lien de mot de passe.
            UUID own = Optional.of(Hibernate.unproxy(user, UserEntity.class))
                    .filter(EstablishmentUserEntity.class::isInstance)
                    .map(EstablishmentUserEntity.class::cast)
                    .map(EstablishmentUserEntity::getEstablishment)
                    .map(EstablishmentEntity::getId)
                    .orElseThrow(() -> new BadRequestException(
                            "This account is not attached to an establishment"));
            if (!own.equals(establishmentId)) {
                throw new BadRequestException(
                        String.format("User %s does not belong to establishment %s", userId, establishmentId));
            }

            this.sendResetPasswordEmail(InitResetPasswordRequest.builder()
                    .username(user.getUsername())
                    .target(RoleTarget.PARTNER)
                    .user(user).build(), EmailTemplateType.WELCOME_USER);
            return this.userMapper.toDto(user);
        } catch (NotFoundException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public UserDto createMobileUser(MobileUserSignupForm signupForm) throws BusinessException {
        try {
            this.validateContacts(signupForm.getContacts());
            RoleEntity role = this.roleRepository.findByCode(RoleType.STUDENT_PARENT.name())
                    .orElseThrow(() -> new NotFoundException(String.format("Role with code %s not found", RoleType.STUDENT_PARENT.name())));

            StudentParentUserEntity user = this.userMapper.toEntity(signupForm);
            user.setRole(role);
            UserEntity savedUser = this.userRepository.save(user);
            if (FunctionalUtils.isTrue(signupForm.isSendEmail())) {
                ContactCreationForm contactCreationForm = signupForm.getContacts().stream()
                        .filter(ContactCreationForm::getIsPrimary).findFirst().get();
                if (ContactType.EMAIL.equals(contactCreationForm.getType())) {
                    this.sendMobileOtpEmail(contactCreationForm.getValue(), WELCOME_MOBILE_USER);
                }
                if (ContactType.PHONE_NUMBER.equals(contactCreationForm.getType())) {
                    this.sendMobileOtpSms(contactCreationForm.getValue());
                }
            }

            return this.userMapper.toDto(savedUser);
        } catch (NotFoundException | DuplicateResourceException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }


    @Override
    public UserDto update(UUID userId, UserUpdateForm updateForm) throws BusinessException {
        // Hors du try : un refus de propriété est un 403 que le handler global doit voir tel quel,
        // pas une Exception ré-emballée en 500. Le contrôle vient avant toute lecture ou écriture.
        this.currentUserProvider.assertCanManageUserAccount(userId);
        try {
            UserEntity userToUpdate = this.userRepository.findById(userId)
                    .orElseThrow(() -> new
                            NotFoundException(String
                            .format("User with id %s not found", userId)));
            this.userMapper.updateProfile(updateForm, userToUpdate);
            if (!CollectionUtils.isEmpty(updateForm.getContacts())) {
                long count = updateForm.getContacts().stream().filter(ContactUpdateForm::getIsPrimary).count();
                if (count == 0 | count > 1) {
                    throw new ValidationException("contacts", "Cannot create a new establishment without or more primary contacts");
                }
                this.assertPrimaryContactNotTaken(userId, updateForm);
                this.manageContacts(updateForm, userToUpdate);
            }
            return this.userMapper.toDto(this.userRepository.save(userToUpdate));
        } catch (NotFoundException | DuplicateResourceException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    /**
     * Refuse un contact principal déjà porté par un autre compte.
     *
     * <p>Deux comptes de même contact principal rendent {@code findByPrimaryContact} ambigu, et
     * l'échec ne tombe pas dans un contrôleur mais dans le filtre JWT, en 500 pour toute la
     * plateforme. La création vérifie déjà l'unicité ({@code validateContacts}) ; la mise à jour le
     * faisait nulle part, et c'était la porte.
     *
     * <p>Le contrôle vise l'utilisateur, pas l'établissement : {@code findByPrimaryContact} ne
     * regarde que {@code user_contacts}. Deux écoles peuvent partager un standard ; deux comptes,
     * jamais leur identifiant de connexion. Rester sur son propre contact est permis — il se
     * retrouve alors soi-même.
     */
    private void assertPrimaryContactNotTaken(UUID userId, UserUpdateForm updateForm) {
        updateForm.getContacts().stream()
                .filter(ContactUpdateForm::getIsPrimary)
                .map(ContactUpdateForm::getValue)
                .filter(StringUtils::isNotBlank)
                .forEach(value -> this.userRepository.findByPrimaryContact(value)
                        .filter(owner -> !Objects.equals(owner.getId(), userId))
                        .ifPresent(owner -> {
                            throw new DuplicateResourceException(String.format(
                                    "Le contact principal %s est déjà utilisé par un autre compte", value));
                        }));
    }

    @Override
    public UserDto changePassword(ChangePasswordForm changePasswordForm) throws BusinessException {
        try {
            // L'utilisateur visé est celui qui appelle, jamais celui que désigne le formulaire.
            // Se fier au `username` du corps laissait un compte authentifié changer le mot de passe
            // d'un autre s'il en connaissait l'actuel, et surtout distinguer « compte inconnu » de
            // « mot de passe erroné » : de quoi énumérer les comptes de la plateforme.
            UserEntity user = this.currentUserProvider.currentUser();
            if (!this.passwordEncoder.matches(changePasswordForm.getOldPassword(), user.getPassword())) {
                throw new BadRequestException("Le mot de passe ne respecte pas les règles de sécurité.");
            }
            String newPassword = this.passwordEncoder.encode(changePasswordForm.getNewPassword());
            user.getPasswordValue().setValue(newPassword);
            return this.userMapper.toDto(this.userRepository.save(user));
        } catch (BadRequestException | NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public UserDto initResetPassword(InitResetPasswordForm resetPasswordForm) throws BusinessException {
        try {
            UserEntity user = this.userRepository.findByPrimaryContact(resetPasswordForm.getUsername())
                    .orElseThrow(() ->
                            new NotFoundException(String.format("Cannot find user with provided email %s",
                                    resetPasswordForm.getUsername())));
            this.sendResetPasswordEmail(InitResetPasswordRequest.builder()
                    .username(resetPasswordForm.getUsername())
                    .target(targetOf(user))
                    .user(user).build(), EmailTemplateType.RESET_PASSWORD);
            return this.userMapper.toDto(user);
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public UserDto initMobileResetPassword(InitResetPasswordForm resetPasswordForm) throws BusinessException {
        try {
            UserEntity user = this.userRepository.findByPrimaryContact(resetPasswordForm.getUsername())
                    .orElseThrow(() ->
                            new NotFoundException(String.format("Cannot find user with provided email %s",
                                    resetPasswordForm.getUsername())));
            ContactEntity contact = user.getContacts().stream()
                    .filter(ContactEntity::isPrimary).findFirst().get();
            if (ContactType.EMAIL.equals(contact.getType())) {
                log.info("init reset password email for user with id {}", user.getId());
                this.sendMobileOtpEmail(contact.getValue(), RESET_MOBILE_PASSWORD);
            }
            if (ContactType.PHONE_NUMBER.equals(contact.getType())) {
                log.info("init reset password phone number for user with id {}", user.getId());
                this.sendMobileOtpSms(contact.getValue());
            }
            return this.userMapper.toDto(user);
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }


    @Override
    public UserDto resetPassword(ResetPasswordForm resetPasswordForm) throws BusinessException {
        try {
            UserEntity user = this.userRepository.findByPrimaryContact(resetPasswordForm.getUsername())
                    .orElseThrow(() ->
                            new NotFoundException(String.format("Cannot find user with provided username %s", resetPasswordForm.getUsername())));

            if (CredentialsUtils.isNotValidPassword(resetPasswordForm.getPassword())) {
                throw new BadRequestException("Le mot de passe ne respecte pas les règles de sécurité.");
            }
            String key = String.format(PARTNER_ROOT_USER_EMAIL_KEY, resetPasswordForm.getUsername());
            String token = (String) this.cacheService.getValue(key);
            if (!StringUtils.equalsIgnoreCase(token, resetPasswordForm.getToken())) {
                throw new BadRequestException("Le lien est expiré ou invalide.");
            }
            String newPassword = this.passwordEncoder.encode(resetPasswordForm.getPassword());
            if (Objects.isNull(user.getPasswordValue())) {
                user.setPasswordValue(PasswordEntity.builder().build());
            }
            user.getPasswordValue().setValue(newPassword);
            this.userRepository.save(user);
            // Le jeton de réinitialisation est neutralisé par la suppression de sa clé de cache
            // ci-dessus : un JWT est stateless et ne peut pas être révoqué par lui-même.
            this.cacheService.delete(key);
            return this.userMapper.toDto(user);
        } catch (NotFoundException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public UserDto resetMobilePassword(ResetMobilePasswordForm resetPasswordForm) throws BusinessException {
        try {
            return this.setNewPassword(resetPasswordForm.getUsername(),
                    resetPasswordForm.getPassword(), resetPasswordForm.getOtp());
        } catch (NotFoundException | BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public UserDto getMe() throws BusinessException {
        try {
            UserEntity current = this.identityService.getCurrentUser();
            UserDto dto = this.userMapper.toDto(current);
            // Le type et l'établissement ne sont pas portés par UserEntity mais par ses
            // sous-classes : MapStruct ne peut pas les déduire, on les pose ici.
            dto.setUserType(userTypeOf(current));
            if (current instanceof EstablishmentUserEntity establishmentUser
                    && Objects.nonNull(establishmentUser.getEstablishment())) {
                dto.setEstablishmentId(establishmentUser.getEstablishment().getId());
                dto.setEstablishmentName(establishmentUser.getEstablishment().getName());
            }
            return dto;
        } catch (UnAuthenticatedUserException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    private static String userTypeOf(UserEntity user) {
        if (user instanceof AdminUserEntity) {
            return UserType.ADMIN_USER;
        }
        if (user instanceof EstablishmentUserEntity) {
            return UserType.ESTABLISHMENT_USER;
        }
        if (user instanceof StudentParentUserEntity) {
            return UserType.STUDENT_PARENT_USER;
        }
        return null;
    }

    @Override
    public void updateLastLogin(String userName) throws BusinessException {
        try {
            this.userRepository.setLastLogin(Instant.now(), userName);
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public TokenIntrospection parseJwt(String token) throws BusinessException {
        try {
            return this.authService.parseJwt(token);
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public UserDto getMe(UUID id) throws BusinessException {
        try {
            UserEntity user = this.userRepository.findById(id)
                    .orElseThrow(() ->
                            new NotFoundException(String.format("Cannot find user with provided id %s", id)));

            return this.userMapper.toDto(user);
        } catch (UnAuthenticatedUserException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public CheckResourceDto otpExists(String username, String otp) throws BusinessException {
        try {
            if (!this.userRepository.existsByPrimaryContact(username)) {
                throw new NotFoundException(String.format("Cannot find user with provided id %s", username));
            }
            String otpInCache = (String) this.cacheService.getValue(String.format(MOBILE_USER_KEY, username));
            if (StringUtils.isBlank(otpInCache) || !StringUtils.equalsIgnoreCase(otp, otpInCache)) {
                return CheckResourceDto.builder()
                        .value(username)
                        .status(CheckResourceStatus.INVALID)
                        .build();
            }
            return CheckResourceDto.builder()
                    .value(username)
                    .status(CheckResourceStatus.VALID)
                    .build();
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public void resendMobileOtp(String username) throws BusinessException {
        try {
            String otpInCache = (String) this.cacheService.getValue(String.format(MOBILE_USER_KEY, username));
            if (StringUtils.isNotBlank(otpInCache)) {
                throw new DuplicateResourceException("Un code a déjà été envoyé. Patientez avant d'en redemander un.");
            }
            Optional<UserEntity> optionalUser = this.userRepository.findByPrimaryContact(username);
            if (optionalUser.isEmpty()) {
                throw new NotFoundException(String.format("Cannot find user with provided id %s", username));
            }
            Optional<ContactEntity> optionalPrimaryContact = optionalUser.get()
                    .getContacts()
                    .stream()
                    .filter(ContactEntity::isPrimary)
                    .findFirst();
            if (optionalPrimaryContact.isEmpty()) {
                throw new NotFoundException(String.format("User with username %s has not primary contact", username));
            }
            ContactEntity primaryContact = optionalPrimaryContact.get();
            if (ContactType.EMAIL.equals(primaryContact.getType())) {
                this.sendMobileOtpEmail(username, RESEND_MOBILE_OTP);
            } else if (ContactType.PHONE_NUMBER.equals(primaryContact.getType())) {
                this.sendMobileOtpSms(username);
            }

        } catch (DuplicateResourceException | NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    @Override
    public void sendStudentParentInvitation(ContactEntity contact) throws BusinessException {
        if (ContactType.EMAIL.equals(contact.getType())) {
            this.sendStudentWelcomeParentEmail(contact.getValue());
        }
        if (ContactType.PHONE_NUMBER.equals(contact.getType())) {
            this.sendStudentWelcomeParentSms(contact.getValue());
        }
    }

    @Override
    public List<StudentLiteDto> findStudents(UUID parentId) throws BusinessException {
        // Même garde que la mise à jour, et pour la même raison : la liste des enfants — matricule
        // et date de naissance, soit deux des trois preuves de rattachement — ne se lit que pour son
        // propre compte. Hors du try pour que le refus reste un 403.
        this.currentUserProvider.assertCanManageUserAccount(parentId);
        try {
            if (!this.userRepository.existsById(parentId)) {
                throw new NotFoundException(String.format("Cannot find user with provided id %s", parentId));
            }
            return this.studentMapper.toLiteDtos(this.studentRepository.findByParentUsers_Id(parentId));
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }

    private void sendMobileOtpSms(String username) {
        String otp = RandomStringUtils.randomNumeric(RESET_PWD_OTP_LENGTH);
        long timeout = Long.parseLong((String) this.globalParameterService
                .getOrDefaultValue(GlobalParameterKey.RESET_USER_PWD_OTP_DELAY.name(), "5"));
        this.cacheService.saveValueWithExpiration(String.format(MOBILE_USER_KEY, username)
                , otp, timeout);
        this.smsService.sendTextMessage(this.messageSource.getMessage(
                        SmsTemplateType.WELCOME_MOBILE_USER.getValue(),
                        new Object[]{otp, timeout}, CurrentLocale.getValue()),
                username);
    }

    private UserDto setNewPassword(String username, String password, String cacheValue) {
        UserEntity user = this.userRepository.findByPrimaryContact(username)
                .orElseThrow(() ->
                        new NotFoundException(String.format("Cannot find user with provided username %s", username)));

        if (CredentialsUtils.isNotValidPassword(password)) {
            throw new BadRequestException("Le mot de passe ne respecte pas les règles de sécurité.");
        }
        String key = String.format(MOBILE_USER_KEY, username);
        String otp = (String) this.cacheService.getValue(key);
        if (!StringUtils.equalsIgnoreCase(otp, cacheValue)) {
            throw new BadRequestException("Le code est expiré ou invalide.");
        }
        String newPassword = this.passwordEncoder.encode(password);
        if (Objects.isNull(user.getPasswordValue())) {
            user.setPasswordValue(PasswordEntity.builder().build());
        }
        user.getPasswordValue().setValue(newPassword);
        this.userRepository.save(user);
        this.cacheService.delete(key);
        return this.userMapper.toDto(user);
    }

    private void sendResetPasswordEmail(InitResetPasswordRequest resetPasswordRequest, EmailTemplateType templateType) {
        try {
            String email = resetPasswordRequest.getUsername();
            UserEntity user = resetPasswordRequest.getUser();
            long timeout = Long.parseLong((String) this.globalParameterService
                    .getOrDefaultValue(GlobalParameterKey.RESET_USER_PWD_TOKEN_DELAY.name(), "60"));
            String token = this.authService.generateResetPasswordToken(
                    ResetPwdRequest.builder()
                            .user(user)
                            .validityInMinutes(timeout)
                            .build());
            this.cacheService.saveValueWithExpiration(String.format(PARTNER_ROOT_USER_EMAIL_KEY, email), token, timeout);
            Context context = this.emailDefaultProperties.getDefaultContext();
            context.setVariable(EmailConstants.TOKEN_LINK, this.buildTokenLink(email, token, resetPasswordRequest.getTarget()));
            context.setVariable(EmailConstants.EMAIL, email);
            this.emailService.send(context, templateType);
        } catch (Exception e) {
            log.error("Error while fetching user with identifier ", e);
        }
    }

    private void sendMobileOtpEmail(String email, EmailTemplateType templateType) {
        try {
            long timeout = Long.parseLong((String) this.globalParameterService
                    .getOrDefaultValue(GlobalParameterKey.RESET_USER_PWD_OTP_DELAY.name(), "5"));
            String otp = RandomStringUtils.randomNumeric(RESET_PWD_OTP_LENGTH);
            this.cacheService.saveValueWithExpiration(String.format(MOBILE_USER_KEY, email), otp, timeout);
            Context context = this.emailDefaultProperties.getDefaultContext();
            context.setVariable(EmailConstants.OTP, otp);
            context.setVariable(EmailConstants.OTP_VALIDITY, timeout);
            context.setVariable(EmailConstants.EMAIL, email);
            this.emailService.send(context, templateType);
        } catch (Exception e) {
            log.error("Error while fetching user with identifier ", e);
        }
    }

    public void sendStudentWelcomeParentEmail(String email) {
        try {
            Context context = this.emailDefaultProperties.getDefaultContext();
            context.setVariable(EmailConstants.EMAIL, email);
            this.emailService.send(context, EmailTemplateType.INVITE_STUDENT_PARENT);
        } catch (Exception e) {
            log.error("Error while fetching user with identifier ", e);
        }
    }

    public void sendStudentWelcomeParentSms(String username) {
        this.smsService.sendTextMessage(this.messageSource.getMessage(
                        SmsTemplateType.INVITE_STUDENT_PARENT_SMS.getValue(),
                        new Object[]{this.emailConfigProperties.getPlatformName()}, CurrentLocale.getValue()),
                username);
    }

    /**
     * Le portail où renvoyer ce compte.
     *
     * <p>{@code initResetPassword} — le « mot de passe oublié » des portails — ne posait pas de
     * cible du tout. {@code RoleTarget.ADMIN.equals(null)} valant faux, un administrateur YPYit
     * recevait un lien vers le portail des écoles, où son compte n'a rien à faire.
     *
     * <p>Le dé-proxyfiage n'est pas décoratif : les comptes héritent d'une table unique, et un
     * {@code instanceof} sur un proxy de la super-classe répond faux — c'est le défaut que
     * {@code CurrentUserProvider} et {@code ReceiptAudience} corrigent déjà de la même façon.
     *
     * <p>Une famille tombe du côté partenaire : elle n'a pas de portail web, son parcours de mot de
     * passe passe par l'application et un code à usage unique.
     */
    private static RoleTarget targetOf(UserEntity user) {
        return Hibernate.unproxy(user, UserEntity.class) instanceof AdminUserEntity
                ? RoleTarget.ADMIN : RoleTarget.PARTNER;
    }

    private String buildTokenLink(String email, String token, RoleTarget target) throws URISyntaxException {
        String url = RoleTarget.ADMIN.equals(target) ? this.emailConfigProperties.getResetPasswordAdminUrl() :
                this.emailConfigProperties.getResetPasswordPartnerUrl();

        URIBuilder ub = new URIBuilder(url);
        ub.setPath(this.emailConfigProperties.getResetPasswordUri());
        ub.setParameter("username", email);
        ub.setParameter("token", token);
        return ub.build().toString();
    }

    private void manageContacts(UserUpdateForm updateForm, UserEntity user) {
        if (CollectionUtils.isEmpty(user.getContacts())) {
            return;
        }
        FunctionalUtils.checkDuplicatedOnUpdate(updateForm.getContacts());
        Set<ContactEntity> contacts = this.contactService.createOrUpdate(updateForm.getContacts());
        user.getContacts().clear();
        user.getContacts().addAll(contacts);
    }

    private void validateContacts(Set<ContactCreationForm> contacts) {
        long count = FunctionalUtils.safelyGetStream(contacts)
                .filter(ContactCreationForm::getIsPrimary).count();
        if (count == 0) {
            throw new BadRequestException("Un compte doit avoir un contact principal.");
        }
        if (count > 1) {
            throw new BadRequestException("Un compte ne peut avoir qu'un seul contact principal.");
        }
        FunctionalUtils.checkDuplicatedOnCreation(contacts);
        contacts.forEach(contact -> {
            if (this.userRepository.existsByPrimaryContact(contact.getValue())) {
                throw new DuplicateResourceException(
                        String.format("User with contact type %s and contact value %s already exists", contact.getType(), contact.getValue()));
            }
        });
    }
}
