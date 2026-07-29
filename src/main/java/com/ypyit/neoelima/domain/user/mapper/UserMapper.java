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
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {


    UserEntity toEntity(UserSignupForm signupForm);

    @Mapping(ignore = true, target = "establishment")
    EstablishmentUserEntity toRootEntity(UserSignupForm signupForm);

    EstablishmentUserEntity toPrincipalEntity(EstablishmentPrincipalCreationForm signupForm);

    EstablishmentUserEntity toEntity(EstablishmentUserSignupForm signupForm);

    StudentParentUserEntity toEntity(MobileUserSignupForm signupForm);

    @Mapping(ignore = true, target = "contacts")
    void updateProfile(UserUpdateForm updateForm, @MappingTarget UserEntity user);

    UserDto toDto(UserEntity entity);
}
