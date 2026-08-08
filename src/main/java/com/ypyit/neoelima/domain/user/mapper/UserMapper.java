package com.ypyit.neoelima.domain.user.mapper;

import com.ypyit.neoelima.domain.user.dto.UserDto;
import com.ypyit.neoelima.domain.user.entity.EstablishmentUserEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.form.EstablishmentPrincipalCreationForm;
import com.ypyit.neoelima.domain.user.form.EstablishmentUserSignupForm;
import com.ypyit.neoelima.domain.user.form.MobileUserSignupForm;
import com.ypyit.neoelima.domain.user.form.UserSignupForm;
import com.ypyit.neoelima.domain.user.form.UserUpdateForm;
import com.ypyit.neoelima.domain.user.mapper.ContactMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = ContactMapper.class, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {


    UserEntity toEntity(UserSignupForm signupForm);

    @Mapping(ignore = true, target = "establishment")
    EstablishmentUserEntity toRootEntity(UserSignupForm signupForm);

    EstablishmentUserEntity toPrincipalEntity(EstablishmentPrincipalCreationForm signupForm);

    EstablishmentUserEntity toEntity(EstablishmentUserSignupForm signupForm);

    StudentParentUserEntity toEntity(MobileUserSignupForm signupForm);

    @Mapping(ignore = true, target = "contacts")
    void updateProfile(UserUpdateForm updateForm, @MappingTarget UserEntity user);

    @Mapping(target = "roleCode", source = "role.code")
    @Mapping(target = "permissions", expression = "java(permissionCodesOf(entity))")
    UserDto toDto(UserEntity entity);

    /**
     * Codes de permission portés par le rôle du compte.
     *
     * <p>Écrit à la main plutôt que dérivé de {@code getAuthorities()} : cette méthode vient de
     * Spring Security et un jour où sa définition changerait, le portail se mettrait à afficher
     * autre chose que les permissions du rôle.
     */
    default java.util.Set<String> permissionCodesOf(UserEntity entity) {
        if (entity == null || entity.getRole() == null || entity.getRole().getPermissions() == null) {
            return java.util.Set.of();
        }
        return entity.getRole().getPermissions().stream()
                .map(com.ypyit.neoelima.domain.user.entity.PermissionEntity::getCode)
                .collect(java.util.stream.Collectors.toSet());
    }
}
