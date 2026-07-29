package com.ypyit.neoelima.domain.user.service.impl;

import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserEntity loadUserByUsername(String username) throws BusinessException {
        try {
            Optional<UserEntity> user = userRepository.findByPrimaryContact(username);
            if (user.isEmpty()) {
                log.error("user {} not found in data base", username);
                throw new UsernameNotFoundException("user provided not found");
            }
            return user.get();
        } catch (UsernameNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }
}
