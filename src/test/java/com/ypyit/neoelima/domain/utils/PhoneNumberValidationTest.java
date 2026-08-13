package com.ypyit.neoelima.domain.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un numéro se juge sur ses chiffres, pas sur la façon dont on les groupe.
 *
 * <p>La règle précédente décrivait un découpage — indicatif puis quatre groupes — et refusait
 * {@code +225 07 11 22 33 44}, soit un numéro ivoirien écrit comme les Ivoiriens l'écrivent. Le
 * défaut est resté invisible jusqu'au jour où la saisie des coordonnées de l'établissement a été
 * ouverte dans le portail : personne n'y tapait de numéro avant.
 */
class PhoneNumberValidationTest {

    @ParameterizedTest
    @DisplayName("les graphies qu'une école emploie réellement sont acceptées")
    @ValueSource(strings = {
            "+225 07 11 22 33 44",   // celle qui était refusée
            "+2250711223344",
            "0711223344",
            "07 11 22 33 44",
            "+225-07-11-22-33-44",
            "+225 (07) 11.22.33.44",
    })
    void acceptsEveryUsualSpelling(String phoneNumber) {
        assertThat(CredentialsUtils.isValidPhoneNumber(phoneNumber)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("ce qui n'est pas un numéro reste refusé")
    @ValueSource(strings = {
            "0711",                  // trop court pour être un numéro
            "0711223344556677889",   // au-delà des quinze chiffres d'E.164
            "07 11 22 AB 44",        // des lettres
            "appelez l'école",
            " ",
    })
    void refusesWhatIsNotANumber(String candidate) {
        assertThat(CredentialsUtils.isValidPhoneNumber(candidate)).isFalse();
    }
}
