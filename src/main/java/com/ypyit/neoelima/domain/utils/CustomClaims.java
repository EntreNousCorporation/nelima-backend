package com.ypyit.neoelima.domain.utils;

public final class CustomClaims {

    public static final String USERNAME = "username";
    public static final String SESSION_ID = "session_id";
    public static final String LAST_LOGIN = "last_login";
    public static final String ESTABLISHMENT_ID = "establishment_id";
    public static final String PERMISSIONS = "permissions";
    public static final String PROFILE = "profile";
    public static final String FIRST_NAME = "firstName";
    public static final String LASTNAME = "lastName";

    private CustomClaims() {
        throw new UnsupportedOperationException("CustomClaims may not be instantiated");
    }
}
