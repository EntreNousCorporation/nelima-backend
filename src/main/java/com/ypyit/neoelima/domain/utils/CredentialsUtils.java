package com.ypyit.neoelima.domain.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public final class CredentialsUtils {

    private static final String VALID_STRING_PASSWORD_REGEX = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[_!@#&()–[{}]:;',?/*~$^+=<>]).{6,16}$";
    private static final String PHONE_NUMBER_REGEX = "^\\+?\\d{1,4}?[-.\\s]?\\(?\\d{1,3}?\\)?[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,9}$";


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
        Pattern pattern = Pattern.compile(PHONE_NUMBER_REGEX);
        Matcher matcher = pattern.matcher(phoneNumber);

        return matcher.matches();
    }
}
