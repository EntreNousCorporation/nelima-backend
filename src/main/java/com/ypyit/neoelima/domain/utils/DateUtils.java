package com.ypyit.neoelima.domain.utils;

import com.ypyit.neoelima.common.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Slf4j
public final class DateUtils {
    public static final String DATE_PATTERN = "dd/MM/yyyy";
    public static final String CUSTOMER_BASE_DATE_PATTERN = "yyyy-MM-dd";
    public static final String CUSTOM_DATE_PATTERN = "yyMMddHHmmss";

    public static DateTimeFormatter CUSTOMER_BASE_DATETIME_PATTERN_FORMAT =

            DateTimeFormatter.ofPattern(CUSTOMER_BASE_DATE_PATTERN);
    public static SimpleDateFormat CUSTOM_DATE_PATTERN_FORMAT =
            new SimpleDateFormat(CUSTOM_DATE_PATTERN);

    private DateUtils() {
        throw new UnsupportedOperationException("DateUtils may not be instantiated");
    }

    public static Instant getCurrentDate() {
        return Instant.now();
    }

    public static String formatDBDate(String date) {
        if (StringUtils.isBlank(date)) {
            return date;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_PATTERN)
                .withZone(ZoneOffset.UTC);
        try {
            return formatter.format(Instant.parse(date));
        } catch (Exception e) {
            log.error("Cannot format date", e);
        }
        return date;
    }

    public static boolean isValidDates(Instant fromDate, Instant toDate) {
        if (Objects.isNull(fromDate) || Objects.isNull(toDate)) {
            throw new ValidationException("dates", "Compare dates must not be null");
        }
        return toDate.isAfter(fromDate);
    }

}
