package com.ypyit.neoelima.domain.user.service.impl;

import com.ypyit.neoelima.common.exception.BusinessException;
import com.ypyit.neoelima.domain.user.entity.UserEntity;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
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
        } catch (IncorrectResultSizeDataAccessException e) {
            // Deux comptes partagent le même contact principal — un état que la création et la mise
            // à jour interdisent désormais (unicité vérifiée au point d'écriture), mais qui, sur une
            // donnée héritée, doit échouer proprement. Ce chemin traverse le JwtAuthFilter à chaque
            // requête portant un jeton : sans ce filet, findByPrimaryContact remonte une
            // IncorrectResultSizeDataAccessException que le ControllerAdvice ne voit pas, et le
            // conteneur rend un 500 à toute la plateforme. Un identifiant ambigu ne désigne
            // personne : on refuse l'authentification.
            log.error("primary contact {} matches several accounts", username, e);
            throw new UsernameNotFoundException("user provided is ambiguous");
        } catch (Exception e) {
            throw new BusinessException(e);
        }
    }
}
