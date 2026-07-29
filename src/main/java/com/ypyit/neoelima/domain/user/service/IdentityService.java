package com.ypyit.neoelima.domain.user.service;

import com.ypyit.neoelima.common.exception.UnAuthenticatedUserException;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class IdentityService {

    private final UserRepository userRepository;

    public UserEntity getCurrentUser() {
        return this.getConnectedUser();
    }

    private UserEntity getConnectedUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof String) || StringUtils.isBlank((CharSequence) principal)) {
            throw new UnAuthenticatedUserException("Invalid credentials");
        }
        Optional<UserEntity> username = this.userRepository.findByPrimaryContact((String) principal);
        if (username.isEmpty()) {
            throw new UnAuthenticatedUserException("Invalid credentials");
        }
        return username.get();
    }
}
