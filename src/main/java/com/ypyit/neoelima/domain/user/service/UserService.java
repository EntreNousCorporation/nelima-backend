package com.ypyit.neoelima.domain.user.service;

import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.domain.authentication.dto.TokenIntrospection;
import com.ypyit.neoelima.domain.establishment.dto.StudentLiteDto;
import com.ypyit.neoelima.domain.transverse.dto.CheckResourceDto;
import com.ypyit.neoelima.domain.user.dto.UserDto;
import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.form.ChangePasswordForm;
import com.ypyit.neoelima.domain.user.form.EstablishmentUserSignupForm;
import com.ypyit.neoelima.domain.user.form.InitResetPasswordForm;
import com.ypyit.neoelima.domain.user.form.MobileUserSignupForm;
import com.ypyit.neoelima.domain.user.form.ResetMobilePasswordForm;
import com.ypyit.neoelima.domain.user.form.ResetPasswordForm;
import com.ypyit.neoelima.domain.user.form.UserSignupForm;
import com.ypyit.neoelima.domain.user.form.UserUpdateForm;

import java.util.List;
import java.util.UUID;

public interface UserService {

    UserDto createPartnerRootUser(UserSignupForm signupForm) throws BusinessException;

    UserDto createPartnerUser(UUID establishmentId, EstablishmentUserSignupForm signupForm) throws BusinessException;

    UserDto createMobileUser(MobileUserSignupForm signupForm) throws BusinessException;

    /**
     * Renvoie à un compte d'école le courriel de bienvenue et son lien de définition de mot de passe.
     *
     * <p>Le premier courriel se perd — filtre anti-pourriel, adresse mal saisie, jeton laissé
     * expirer. Sans ce renvoi, la seule issue était de supprimer le compte et de le recréer.
     *
     * <p>L'établissement est passé en clair et l'appartenance du compte est <strong>vérifiée</strong>,
     * jamais supposée : sinon l'identifiant d'un compte d'une autre école suffirait à lui expédier
     * un lien de mot de passe valide.
     */
    UserDto resendActivationLink(UUID establishmentId, UUID userId) throws BusinessException;

    UserDto update(UUID id, UserUpdateForm signupForm) throws BusinessException;

    UserDto changePassword(ChangePasswordForm changePasswordForm) throws BusinessException;

    UserDto initResetPassword(InitResetPasswordForm initResetPasswordForm) throws BusinessException;

    UserDto initMobileResetPassword(InitResetPasswordForm initResetPasswordForm) throws BusinessException;

    UserDto resetPassword(ResetPasswordForm resetPasswordForm) throws BusinessException;

    UserDto resetMobilePassword(ResetMobilePasswordForm resetPasswordForm) throws BusinessException;

    UserDto getMe() throws BusinessException;

    void updateLastLogin(String userName) throws BusinessException;

    TokenIntrospection parseJwt(String token) throws BusinessException;

    UserDto getMe(UUID id) throws BusinessException;

    CheckResourceDto otpExists(String username, String otp) throws BusinessException;

    void resendMobileOtp(String username) throws BusinessException;

    void sendStudentParentInvitation(ContactEntity contact) throws BusinessException;

    List<StudentLiteDto> findStudents(UUID parentId) throws BusinessException;
}
