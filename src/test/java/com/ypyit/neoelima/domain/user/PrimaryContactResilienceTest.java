package com.ypyit.neoelima.domain.user;

import com.ypyit.neoelima.domain.user.entity.ContactEntity;
import com.ypyit.neoelima.domain.user.entity.StudentParentUserEntity;
import com.ypyit.neoelima.domain.user.enums.ContactType;
import com.ypyit.neoelima.domain.user.repository.UserRepository;
import com.ypyit.neoelima.domain.user.service.impl.UserDetailsServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Le contact principal casse en douceur, jamais en 500.
 *
 * <p>Deux points, sur le même chemin critique : {@code getUsername()} est appelé pour chaque tuteur
 * à la sérialisation d'un élève, et {@code loadUserByUsername} traverse le filtre JWT à chaque
 * requête portant un jeton. Une donnée abîmée à l'un ou l'autre ne doit pas rompre toute la fratrie
 * ni toute la plateforme.
 */
class PrimaryContactResilienceTest {

    @Test
    @DisplayName("un tuteur sans contact principal rend un username nul, sans exception")
    void usernameIsNullWhenNoPrimaryContact() {
        StudentParentUserEntity brokenTutor = StudentParentUserEntity.builder()
                .firstName("Co").lastName("Tuteur")
                .contacts(new HashSet<>(List.of(ContactEntity.builder()
                        .type(ContactType.EMAIL).value("secondaire@gmail.com").isPrimary(false).build())))
                .build();

        // Avant : IllegalArgumentException, et StudentMapper échouait pour toute la fratrie de cet
        // élève — 400 sur /students/mine, 500 sur /students.
        assertThat(brokenTutor.getUsername()).isNull();
    }

    @Test
    @DisplayName("un contact principal ambigu refuse l'authentification, il ne la casse pas")
    void ambiguousPrimaryContactYieldsAuthFailureNotServerError() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByPrimaryContact(anyString()))
                .thenThrow(new IncorrectResultSizeDataAccessException(1, 2));
        UserDetailsServiceImpl service = new UserDetailsServiceImpl(repository);

        // Sans le filet, cette IncorrectResultSizeDataAccessException remontait le filtre JWT et
        // le conteneur rendait 500. Un identifiant qui désigne deux comptes ne désigne personne.
        assertThatThrownBy(() -> service.loadUserByUsername("collision@gmail.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("un contact principal introuvable reste un refus propre")
    void unknownPrimaryContactStaysAUsernameNotFound() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByPrimaryContact(anyString())).thenReturn(Optional.empty());
        UserDetailsServiceImpl service = new UserDetailsServiceImpl(repository);

        assertThatThrownBy(() -> service.loadUserByUsername("inconnu@gmail.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
