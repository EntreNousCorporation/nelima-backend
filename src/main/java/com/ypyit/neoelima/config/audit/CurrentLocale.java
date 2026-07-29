package com.ypyit.neoelima.config.audit;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class CurrentLocale {

    private static final AtomicReference<Locale> SHARED_LOCALE = new AtomicReference<>();

    public static Locale getValue() {
        Locale current = SHARED_LOCALE.get();
        if (Objects.isNull(current)) {
            return Locale.FRANCE;
        }
        return current;
    }

    public static void setValue(Locale locale) {
        SHARED_LOCALE.set(locale);
    }
}
