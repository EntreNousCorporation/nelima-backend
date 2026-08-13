package com.ypyit.neoelima.domain.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public final class CredentialsUtils {

    private static final String VALID_STRING_PASSWORD_REGEX = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[_!@#&()–[{}]:;',?/*~$^+=<>]).{6,16}$";

    /**
     * Les caractères qu'un numéro a le droit de contenir, séparateurs compris.
     *
     * <p>La règle précédente décrivait un découpage précis — indicatif, puis quatre groupes de
     * chiffres — et refusait donc {@code +225 07 11 22 33 44}, c'est-à-dire un numéro ivoirien
     * écrit comme les Ivoiriens l'écrivent. Le défaut ne s'est vu qu'en ouvrant la saisie des
     * coordonnées de l'établissement : jusque-là, personne ne tapait de numéro dans le portail.
     *
     * <p>Ce qui compte n'est pas le groupement, qui n'appartient qu'à celui qui saisit, mais le
     * nombre de chiffres. On valide donc la forme d'abord, la longueur ensuite.
     */
    private static final String PHONE_NUMBER_REGEX = "^\\+?[\\d\\s().-]+$";

    /** Huit chiffres au minimum — un numéro local ivoirien en compte dix —, quinze au plus (E.164). */
    private static final int MIN_PHONE_DIGITS = 8;
    private static final int MAX_PHONE_DIGITS = 15;


    private CredentialsUtils() {
        throw new UnsupportedOperationException("CredentialsUtils may not be instantiated");
    }

    public static boolean isValidPassword(String password) {

        if (StringUtils.isBlank(password)) {
            return false;
        }

        return password.matches(VALID_STRING_PASSWORD_REGEX);
    }

    public static boolean isNotValidPassword(String password) {
        return !isValidPassword(password);
    }

    public static boolean isValidPhoneNumber(String phoneNumber) {
        if (StringUtils.isBlank(phoneNumber)) {
            return false;
        }
        Pattern pattern = Pattern.compile(PHONE_NUMBER_REGEX);
        Matcher matcher = pattern.matcher(phoneNumber.trim());
        if (!matcher.matches()) {
            return false;
        }
        long digits = phoneNumber.chars().filter(Character::isDigit).count();
        return digits >= MIN_PHONE_DIGITS && digits <= MAX_PHONE_DIGITS;
    }
}
